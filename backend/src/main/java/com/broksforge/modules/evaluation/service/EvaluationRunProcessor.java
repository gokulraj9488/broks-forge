package com.broksforge.modules.evaluation.service;

import com.broksforge.modules.evaluation.domain.EvaluationResult;
import com.broksforge.modules.evaluation.domain.EvaluationRun;
import com.broksforge.modules.evaluation.domain.EvaluationRunStatus;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.repository.EvaluationResultRepository;
import com.broksforge.modules.evaluation.repository.EvaluationRunRepository;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import com.broksforge.modules.model.ModelInvocationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persists a single evaluation run and its metric results in its own short
 * transaction. Critically, the outbound model call happens <em>before</em> this
 * method (in the executor), so no database connection is held across the network
 * call — the performance issue called out for the Phase 2 health checker is avoided
 * here by construction (see PERFORMANCE_GUIDE.md).
 */
@Service
public class EvaluationRunProcessor {

    private final EvaluationRunRepository runRepository;
    private final EvaluationResultRepository resultRepository;

    public EvaluationRunProcessor(EvaluationRunRepository runRepository,
                                  EvaluationResultRepository resultRepository) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunTotals persistRun(UUID jobId, UUID organizationId, BigDecimal passThreshold, int sequence,
                                UUID datasetItemId, String input, ModelInvocationResult invocation,
                                List<MetricOutcome> outcomes) {
        return persistRun(jobId, organizationId, passThreshold, sequence, datasetItemId, input, invocation,
                outcomes, 1);
    }

    /**
     * As above, but records which execution pass produced this run — 1 for the original run,
     * 2+ when a background job is resumed or a failed row is retried in a later pass.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunTotals persistRun(UUID jobId, UUID organizationId, BigDecimal passThreshold, int sequence,
                                UUID datasetItemId, String input, ModelInvocationResult invocation,
                                List<MetricOutcome> outcomes, int attempt) {
        EvaluationRun run = new EvaluationRun();
        run.setEvaluationJobId(jobId);
        run.setOrganizationId(organizationId);
        run.setDatasetItemId(datasetItemId);
        run.setSequence(sequence);
        run.setAttempt(attempt);
        run.setInput(input);
        run.setLatencyMs(invocation.latencyMs());
        run.setHttpStatus(invocation.httpStatus());
        run.setCompletedAt(Instant.now());

        if (!invocation.success()) {
            run.setStatus(EvaluationRunStatus.FAILED);
            run.setError(invocation.error());
            run.setPassed(false);
            run.setScore(BigDecimal.ZERO);
            run.setCost(invocation.cost());
            runRepository.save(run);
            return new RunTotals(false, false, BigDecimal.ZERO, invocation.latencyMs(), null, invocation.cost());
        }

        run.setStatus(EvaluationRunStatus.SUCCEEDED);
        run.setOutput(invocation.output());
        run.setPromptTokens(invocation.promptTokens());
        run.setCompletionTokens(invocation.completionTokens());
        run.setTotalTokens(invocation.totalTokens());
        run.setCost(invocation.cost());

        // Only metrics that actually ran count toward the run's score — a provider outage on one
        // metric (auth failure, timeout, ...) must not be scored as a quality failure alongside
        // metrics that genuinely ran and failed.
        List<MetricOutcome> completed = outcomes.stream()
                .filter(o -> o.executionStatus() == MetricExecutionStatus.COMPLETED)
                .toList();
        int total = completed.size();
        long passed = completed.stream().filter(o -> Boolean.TRUE.equals(o.passed())).count();
        BigDecimal score;
        boolean overallPassed;
        if (total == 0) {
            // Every configured metric failed to execute — nothing to score, and it can't be
            // called a pass either (that would mask a total infrastructure failure as success).
            score = outcomes.isEmpty() ? BigDecimal.ONE : BigDecimal.ZERO;
            overallPassed = outcomes.isEmpty();
        } else {
            score = BigDecimal.valueOf(passed).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
            overallPassed = passThreshold == null ? passed == total : score.compareTo(passThreshold) >= 0;
        }
        run.setScore(score);
        run.setPassed(overallPassed);

        EvaluationRun savedRun = runRepository.save(run);
        for (MetricOutcome outcome : outcomes) {
            EvaluationResult result = new EvaluationResult();
            result.setEvaluationRunId(savedRun.getId());
            result.setEvaluationJobId(jobId);
            result.setOrganizationId(organizationId);
            result.setMetricType(outcome.type());
            result.setMetricLabel(outcome.label());
            result.setPassed(outcome.passed());
            result.setScore(outcome.score());
            result.setThreshold(outcome.threshold());
            result.setDetail(outcome.detail());
            result.setExecutionStatus(outcome.executionStatus());
            resultRepository.save(result);
        }
        return new RunTotals(true, overallPassed, score, invocation.latencyMs(), invocation.totalTokens(),
                invocation.cost());
    }
}
