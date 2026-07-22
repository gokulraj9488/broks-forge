package com.broksforge.modules.evaluation.service;

import com.broksforge.common.util.TemplateVariables;
import com.broksforge.modules.dataset.service.DatasetRow;
import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.service.metric.EvaluationMetricEngine;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import com.broksforge.modules.model.ModelInvocationRequest;
import com.broksforge.modules.model.ModelInvocationResult;
import com.broksforge.modules.model.ModelInvocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs an {@link EvaluationPlan}: for each dataset row it renders the input, invokes
 * the target (network call, no transaction), scores the metrics, and persists the
 * run via {@link EvaluationRunProcessor} in a short transaction. It then aggregates
 * the job summary in memory.
 *
 * <p>This component is intentionally framework-light and stateless per call, so it
 * is the natural unit a future async worker will invoke off the request thread
 * (ADR 0005). Execution is sequential today; per-row failures are isolated and do
 * not abort the job.</p>
 */
@Slf4j
@Component
public class EvaluationJobExecutor {

    private final ModelInvocationService modelInvocationService;
    private final EvaluationMetricEngine metricEngine;
    private final EvaluationRunProcessor processor;

    public EvaluationJobExecutor(ModelInvocationService modelInvocationService,
                                 EvaluationMetricEngine metricEngine,
                                 EvaluationRunProcessor processor) {
        this.modelInvocationService = modelInvocationService;
        this.metricEngine = metricEngine;
        this.processor = processor;
    }

    public EvaluationOutcome execute(EvaluationPlan plan) {
        Accumulator acc = new Accumulator();

        int sequence = 0;
        for (DatasetRow row : plan.items()) {
            String input = renderInput(plan.template(), row);
            logRenderDiagnostics(plan.jobId(), plan.template(), row, input);
            ModelInvocationRequest request = new ModelInvocationRequest(
                    plan.organizationId(), plan.projectId(), plan.provider(), plan.model(),
                    input, plan.parameters(), plan.target());

            ModelInvocationResult result = modelInvocationService.invoke(request);

            List<MetricOutcome> outcomes = result.success()
                    ? metricEngine.evaluate(plan.metrics(),
                    new MetricContext(input, result.output(), row.expectedOutput(),
                            result.latencyMs(), result.totalTokens(), result.cost()))
                    : List.of();

            RunTotals totals = processor.persistRun(plan.jobId(), plan.organizationId(), plan.passThreshold(),
                    sequence, row.itemId(), input, result, outcomes);

            acc.record(totals, outcomes);
            sequence++;
        }

        Map<String, Object> summary = buildSummary(acc);
        log.info("Evaluation job {} executed: {} completed, {} failed", plan.jobId(), acc.completed, acc.failed);
        return new EvaluationOutcome(acc.completed, acc.failed, summary);
    }

    /**
     * Mutable per-job accumulator (deliberately not a record — every field is updated once per
     * row). Separates two independent signals the dashboard needs distinct KPIs for:
     * <b>execution health</b> (did the invocation/metric provider calls actually run) and
     * <b>evaluation quality</b> (did the metrics that DID run pass their threshold). A metric
     * whose provider call never completed contributes to neither a pass nor a fail — it is
     * tracked as skipped/unavailable so it can never be misread as a quality failure.
     */
    static final class Accumulator {
        int completed;
        int failed;
        long latencySum;
        int latencyCount;
        long tokenSum;
        BigDecimal costSum = BigDecimal.ZERO;

        // Evaluation KPIs — run-level quality verdict, only over runs whose invocation succeeded.
        int evaluationPassed;
        int evaluationFailed;
        int evaluationSkipped; // invocation succeeded but every configured metric failed to execute

        // Average score — metric-level, only over metrics that actually completed.
        BigDecimal metricScoreSum = BigDecimal.ZERO;
        int completedMetricCount;

        // Execution KPIs — metric-level provider-call health, job-wide across every row/metric.
        final Map<EvaluationMetricType, int[]> metricTallies = new EnumMap<>(EvaluationMetricType.class);

        void record(RunTotals totals, List<MetricOutcome> outcomes) {
            if (!totals.success()) {
                failed++;
                return;
            }
            completed++;
            if (totals.latencyMs() != null) {
                latencySum += totals.latencyMs();
                latencyCount++;
            }
            if (totals.totalTokens() != null) {
                tokenSum += totals.totalTokens();
            }
            if (totals.cost() != null) {
                costSum = costSum.add(totals.cost());
            }

            boolean anyMetricCompleted = outcomes.stream()
                    .anyMatch(o -> o.executionStatus() == MetricExecutionStatus.COMPLETED);
            if (!outcomes.isEmpty() && !anyMetricCompleted) {
                evaluationSkipped++;
            } else if (totals.overallPassed()) {
                evaluationPassed++;
            } else {
                evaluationFailed++;
            }

            for (MetricOutcome outcome : outcomes) {
                int[] tally = metricTallies.computeIfAbsent(outcome.type(), k -> new int[8]);
                if (outcome.executionStatus() == MetricExecutionStatus.COMPLETED) {
                    tally[1]++;
                    if (Boolean.TRUE.equals(outcome.passed())) {
                        tally[0]++;
                    }
                    metricScoreSum = metricScoreSum.add(outcome.score());
                    completedMetricCount++;
                } else {
                    tally[2 + (outcome.executionStatus().ordinal() - 1)]++;
                }
            }
        }
    }

    /**
     * Processes one dataset row for a background (batched) execution pass: invokes the target
     * with retry/exponential-backoff on transient failures (HTTP 429 or 5xx), scores it, and
     * persists the run tagged with {@code attempt}. Used by {@code EvaluationBackgroundRunner};
     * the original synchronous {@link #execute} path is untouched.
     */
    public RunTotals executeRow(EvaluationPlan plan, DatasetRow row, int sequence, int attempt,
                                int maxAttempts, long backoffBaseMs, long backoffMaxMs) {
        String input = renderInput(plan.template(), row);
        logRenderDiagnostics(plan.jobId(), plan.template(), row, input);
        ModelInvocationRequest request = new ModelInvocationRequest(
                plan.organizationId(), plan.projectId(), plan.provider(), plan.model(),
                input, plan.parameters(), plan.target());

        ModelInvocationResult result = invokeWithRetry(request, maxAttempts, backoffBaseMs, backoffMaxMs);

        List<MetricOutcome> outcomes = result.success()
                ? metricEngine.evaluate(plan.metrics(),
                new MetricContext(input, result.output(), row.expectedOutput(),
                        result.latencyMs(), result.totalTokens(), result.cost()))
                : List.of();

        return processor.persistRun(plan.jobId(), plan.organizationId(), plan.passThreshold(),
                sequence, row.itemId(), input, result, outcomes, attempt);
    }

    /**
     * Retries a transient invocation failure (rate-limited or server error) with exponential
     * backoff and jitter, up to {@code maxAttempts}. A non-transient failure (e.g. 4xx other than
     * 429, or a connection refused) is returned immediately — retrying it would just waste time.
     */
    private ModelInvocationResult invokeWithRetry(ModelInvocationRequest request, int maxAttempts,
                                                   long backoffBaseMs, long backoffMaxMs) {
        ModelInvocationResult result = modelInvocationService.invoke(request);
        int attempt = 1;
        while (!result.success() && isRetryable(result) && attempt < maxAttempts) {
            long delay = Math.min(backoffMaxMs, backoffBaseMs * (1L << (attempt - 1)));
            delay += (long) (delay * 0.2 * Math.random());
            log.warn("Retrying model invocation (attempt {}/{}) after {}ms: {}", attempt + 1, maxAttempts, delay,
                    result.error());
            sleep(delay);
            result = modelInvocationService.invoke(request);
            attempt++;
        }
        return result;
    }

    private boolean isRetryable(ModelInvocationResult result) {
        Integer status = result.httpStatus();
        return status == null || status == 429 || status >= 500;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Renders the prompt template against the dataset row. A template that references
     * {@code {{input}}} gets the customer message substituted in place, exactly where the
     * author put it. A template with no {@code {{input}}} placeholder is a bare system
     * prompt (instructions only) — without this fallback the customer's dataset message
     * would never reach the provider at all, and the model would just respond to the
     * static instructions with a generic greeting. So in that case the row's input is
     * appended after the rendered template, guaranteeing the provider always receives
     * both the system instructions and the actual customer request.
     */
    String renderInput(String template, DatasetRow row) {
        if (template == null || template.isBlank()) {
            return row.input();
        }
        Map<String, Object> values = buildTemplateValues(row);
        String rendered = TemplateVariables.render(template, values);
        if (!TemplateVariables.extract(template).contains("input") && row.input() != null && !row.input().isBlank()) {
            rendered = rendered + "\n\n" + row.input();
        }
        return rendered;
    }

    /** The exact variable→value map {@link #renderInput} substitutes against — factored out so
     * diagnostics (see {@link #logRenderDiagnostics}) inspect precisely what rendering saw, not an
     * approximation of it. */
    private Map<String, Object> buildTemplateValues(DatasetRow row) {
        Map<String, Object> values = new LinkedHashMap<>(row.metadata() == null ? Map.of() : row.metadata());
        values.put("input", row.input());
        values.put("expected_output", row.expectedOutput());
        return values;
    }

    /**
     * Root-cause fix: {@link TemplateVariables#render} silently substitutes an empty string for
     * any {@code {{variable}}} with no matching value — a dataset row missing a column the prompt
     * references (a blank cell, a short/ragged CSV row, or a metadata key that doesn't match the
     * template) renders that placeholder as nothing, with no error, and the resulting prompt still
     * gets sent to the model. The model then correctly reports it never received the data — the
     * pipeline behavior was correct, but completely invisible. This makes that gap visible: a
     * per-row DEBUG trace always, and a WARN the moment any referenced variable has no value.
     */
    private void logRenderDiagnostics(UUID jobId, String template, DatasetRow row, String renderedPrompt) {
        if (template == null || template.isBlank()) {
            return;
        }
        Set<String> detected = TemplateVariables.extract(template);
        Map<String, Object> values = buildTemplateValues(row);
        if (log.isDebugEnabled()) {
            log.debug("Evaluation job {} row {} (sequence {}): variables detected={}, resolved={}, rendered=\"{}\"",
                    jobId, row.itemId(), row.sequence(), detected, values.keySet(), renderedPrompt);
        }
        Set<String> missing = TemplateVariables.missingVariables(template, values);
        if (!missing.isEmpty()) {
            log.warn("Evaluation job {} row {} (sequence {}): template variable(s) {} had no matching dataset "
                            + "value and rendered as empty text — check the dataset's column mapping or the "
                            + "prompt's {{variable}} names",
                    jobId, row.itemId(), row.sequence(), missing);
        }
    }

    // Package-private (not private) so EvaluationBackgroundRunner.buildSummaryFromDb can build the
    // same "execution"/"metricBreakdown" shape from a DB aggregate instead of an in-memory Accumulator.
    static final MetricExecutionStatus[] ERROR_STATUSES = {
            MetricExecutionStatus.AUTHENTICATION_ERROR, MetricExecutionStatus.PROVIDER_UNAVAILABLE,
            MetricExecutionStatus.RATE_LIMITED, MetricExecutionStatus.MODEL_NOT_FOUND,
            MetricExecutionStatus.TIMEOUT, MetricExecutionStatus.INFRASTRUCTURE_ERROR,
    };

    Map<String, Object> buildSummary(Accumulator acc) {
        int totalRuns = acc.completed + acc.failed;
        int evaluated = acc.evaluationPassed + acc.evaluationFailed;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", totalRuns);
        summary.put("succeeded", acc.completed);
        summary.put("failed", acc.failed);
        // Kept for backward compatibility with existing consumers (benchmarking/regression read
        // these two generically) — now computed only over runs that were actually evaluated, so
        // a run skipped for a provider outage no longer silently inflates or deflates it.
        summary.put("passed", acc.evaluationPassed);
        summary.put("passRate", evaluated > 0 ? round((double) acc.evaluationPassed / evaluated) : 0.0);
        summary.put("avgLatencyMs", acc.latencyCount > 0 ? round((double) acc.latencySum / acc.latencyCount) : null);
        summary.put("totalTokens", acc.tokenSum);
        summary.put("totalCost", acc.costSum.doubleValue());
        // Metric-level average — only metrics that actually completed contribute a score; an
        // unavailable metric (provider outage) is never averaged in as if it scored zero.
        summary.put("avgScore", acc.completedMetricCount > 0
                ? acc.metricScoreSum.divide(BigDecimal.valueOf(acc.completedMetricCount), 4, RoundingMode.HALF_UP).doubleValue()
                : 0.0);
        summary.put("completedMetricCount", acc.completedMetricCount);

        // Evaluation KPIs — the quality verdict, independent of provider/execution health.
        Map<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("passed", acc.evaluationPassed);
        evaluation.put("failed", acc.evaluationFailed);
        evaluation.put("skipped", acc.evaluationSkipped);
        summary.put("evaluation", evaluation);

        // Execution KPIs — metric-level provider-call health, aggregated job-wide.
        int executionSucceeded = acc.completedMetricCount;
        Map<MetricExecutionStatus, Integer> executionErrorTotals = new EnumMap<>(MetricExecutionStatus.class);
        for (MetricExecutionStatus status : ERROR_STATUSES) {
            executionErrorTotals.put(status, 0);
        }
        for (int[] tally : acc.metricTallies.values()) {
            for (MetricExecutionStatus status : ERROR_STATUSES) {
                executionErrorTotals.merge(status, tally[2 + (status.ordinal() - 1)], Integer::sum);
            }
        }
        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("succeeded", executionSucceeded);
        execution.put("authenticationErrors", executionErrorTotals.get(MetricExecutionStatus.AUTHENTICATION_ERROR));
        execution.put("providerErrors", executionErrorTotals.get(MetricExecutionStatus.PROVIDER_UNAVAILABLE));
        execution.put("rateLimited", executionErrorTotals.get(MetricExecutionStatus.RATE_LIMITED));
        execution.put("modelNotFound", executionErrorTotals.get(MetricExecutionStatus.MODEL_NOT_FOUND));
        execution.put("timeouts", executionErrorTotals.get(MetricExecutionStatus.TIMEOUT));
        execution.put("infrastructureErrors", executionErrorTotals.get(MetricExecutionStatus.INFRASTRUCTURE_ERROR));
        summary.put("execution", execution);
        int unavailableMetricCount = executionErrorTotals.values().stream().mapToInt(Integer::intValue).sum();
        summary.put("unavailableMetricCount", unavailableMetricCount);

        // Kept for backward compatibility — pass rate per metric type, only over completed outcomes.
        Map<String, Object> metricPassRates = new LinkedHashMap<>();
        acc.metricTallies.forEach((type, tally) ->
                metricPassRates.put(type.name(), tally[1] > 0 ? round((double) tally[0] / tally[1]) : 0.0));
        summary.put("metricPassRates", metricPassRates);

        // Full per-metric breakdown — every configured metric type appears, even one that never
        // completed a single time, with its execution-error counts broken out by status.
        Map<String, Object> metricBreakdown = new LinkedHashMap<>();
        acc.metricTallies.forEach((type, tally) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            int completedCount = tally[1];
            int passedCount = tally[0];
            Map<String, Integer> errors = new LinkedHashMap<>();
            for (MetricExecutionStatus status : ERROR_STATUSES) {
                int count = tally[2 + (status.ordinal() - 1)];
                if (count > 0) {
                    errors.put(status.name(), count);
                }
            }
            int total = completedCount + errors.values().stream().mapToInt(Integer::intValue).sum();
            entry.put("total", total);
            entry.put("completed", completedCount);
            entry.put("passed", passedCount);
            entry.put("failed", completedCount - passedCount);
            entry.put("executionErrors", errors);
            metricBreakdown.put(type.name(), entry);
        });
        summary.put("metricBreakdown", metricBreakdown);
        return summary;
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
