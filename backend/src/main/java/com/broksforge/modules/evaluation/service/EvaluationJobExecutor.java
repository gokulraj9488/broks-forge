package com.broksforge.modules.evaluation.service;

import com.broksforge.common.util.TemplateVariables;
import com.broksforge.modules.dataset.service.DatasetRow;
import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
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
        int completed = 0;
        int failed = 0;
        long latencySum = 0;
        int latencyCount = 0;
        long tokenSum = 0;
        BigDecimal costSum = BigDecimal.ZERO;
        int passedRuns = 0;
        BigDecimal scoreSum = BigDecimal.ZERO;
        int scoredRuns = 0;
        Map<EvaluationMetricType, int[]> metricTallies = new EnumMap<>(EvaluationMetricType.class);

        int sequence = 0;
        for (DatasetRow row : plan.items()) {
            String input = renderInput(plan.template(), row);
            ModelInvocationRequest request = new ModelInvocationRequest(
                    plan.organizationId(), plan.projectId(), plan.provider(), plan.model(),
                    input, plan.parameters(), plan.target());

            ModelInvocationResult result = modelInvocationService.invoke(request);

            List<MetricOutcome> outcomes = result.success()
                    ? metricEngine.evaluate(plan.metrics(),
                    new MetricContext(result.output(), row.expectedOutput(),
                            result.latencyMs(), result.totalTokens(), result.cost()))
                    : List.of();

            RunTotals totals = processor.persistRun(plan.jobId(), plan.organizationId(), plan.passThreshold(),
                    sequence, row.itemId(), input, result, outcomes);

            if (totals.success()) {
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
                if (totals.overallPassed()) {
                    passedRuns++;
                }
                scoreSum = scoreSum.add(totals.score());
                scoredRuns++;
                for (MetricOutcome outcome : outcomes) {
                    int[] tally = metricTallies.computeIfAbsent(outcome.type(), k -> new int[2]);
                    tally[1]++;
                    if (outcome.passed()) {
                        tally[0]++;
                    }
                }
            } else {
                failed++;
            }
            sequence++;
        }

        Map<String, Object> summary = buildSummary(completed, failed, latencySum, latencyCount,
                tokenSum, costSum, passedRuns, scoreSum, scoredRuns, metricTallies);
        log.info("Evaluation job {} executed: {} completed, {} failed", plan.jobId(), completed, failed);
        return new EvaluationOutcome(completed, failed, summary);
    }

    private String renderInput(String template, DatasetRow row) {
        if (template == null || template.isBlank()) {
            return row.input();
        }
        Map<String, Object> values = new LinkedHashMap<>(row.metadata() == null ? Map.of() : row.metadata());
        values.put("input", row.input());
        values.put("expected_output", row.expectedOutput());
        return TemplateVariables.render(template, values);
    }

    private Map<String, Object> buildSummary(int completed, int failed, long latencySum, int latencyCount,
                                             long tokenSum, BigDecimal costSum, int passedRuns,
                                             BigDecimal scoreSum, int scoredRuns,
                                             Map<EvaluationMetricType, int[]> metricTallies) {
        int totalRuns = completed + failed;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", totalRuns);
        summary.put("succeeded", completed);
        summary.put("failed", failed);
        summary.put("passed", passedRuns);
        summary.put("passRate", completed > 0 ? round((double) passedRuns / completed) : 0.0);
        summary.put("avgLatencyMs", latencyCount > 0 ? round((double) latencySum / latencyCount) : null);
        summary.put("totalTokens", tokenSum);
        summary.put("totalCost", costSum.stripTrailingZeros().toPlainString());
        summary.put("avgScore", scoredRuns > 0
                ? scoreSum.divide(BigDecimal.valueOf(scoredRuns), 4, RoundingMode.HALF_UP).doubleValue()
                : 0.0);

        Map<String, Object> metricPassRates = new LinkedHashMap<>();
        metricTallies.forEach((type, tally) ->
                metricPassRates.put(type.name(), tally[1] > 0 ? round((double) tally[0] / tally[1]) : 0.0));
        summary.put("metricPassRates", metricPassRates);
        return summary;
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
