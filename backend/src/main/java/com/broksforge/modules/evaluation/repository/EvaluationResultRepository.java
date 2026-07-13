package com.broksforge.modules.evaluation.repository;

import com.broksforge.modules.evaluation.domain.EvaluationResult;
import com.broksforge.modules.evaluation.service.MetricExecutionFailureTally;
import com.broksforge.modules.evaluation.service.MetricFailureTally;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationResultRepository extends JpaRepository<EvaluationResult, UUID> {

    List<EvaluationResult> findByEvaluationRunIdOrderByMetricTypeAsc(UUID evaluationRunId);

    List<EvaluationResult> findByEvaluationRunIdAndEvaluationJobIdOrderByMetricTypeAsc(
            UUID evaluationRunId, UUID evaluationJobId);

    /**
     * Pass/fail counts per metric type for a job — the signal the root-cause engine uses
     * to find the dominant failure mode. {@code SUM(CASE ...)} yields {@code Long}, so the
     * projection record's fields are boxed.
     */
    @Query("""
            SELECT new com.broksforge.modules.evaluation.service.MetricFailureTally(
                r.metricType,
                SUM(CASE WHEN r.passed = true THEN 1L ELSE 0L END),
                SUM(CASE WHEN r.passed = false THEN 1L ELSE 0L END))
            FROM EvaluationResult r
            WHERE r.evaluationJobId = :jobId AND r.executionStatus = com.broksforge.modules.evaluation.domain.MetricExecutionStatus.COMPLETED
            GROUP BY r.metricType
            """)
    List<MetricFailureTally> tallyByMetric(@Param("jobId") UUID jobId);

    /**
     * Per-metric-type, per-status counts of results that never completed (auth/provider/rate-limit/
     * timeout/infrastructure failures) — the root-cause engine's signal for "the judge/embedding
     * call never ran" findings, kept strictly separate from {@link #tallyByMetric} so a provider
     * outage never gets misreported as a low score.
     */
    @Query("""
            SELECT new com.broksforge.modules.evaluation.service.MetricExecutionFailureTally(
                r.metricType, r.executionStatus, COUNT(r))
            FROM EvaluationResult r
            WHERE r.evaluationJobId = :jobId AND r.executionStatus <> com.broksforge.modules.evaluation.domain.MetricExecutionStatus.COMPLETED
            GROUP BY r.metricType, r.executionStatus
            """)
    List<MetricExecutionFailureTally> tallyExecutionFailuresByMetric(@Param("jobId") UUID jobId);

    /**
     * Counts runs whose invocation succeeded but every configured metric failed to execute — the
     * DB-driven equivalent of {@code EvaluationJobExecutor.Accumulator}'s {@code evaluationSkipped}
     * counter, used by {@code EvaluationBackgroundRunner} to reconstruct the same "evaluation"
     * summary section a synchronously-run job gets. A run lands here when it has at least one
     * {@link EvaluationResult} row (metrics were configured and attempted) but none of them
     * completed — by construction ({@code EvaluationRunProcessor#persistRun}) such a run's
     * {@code passed} column is always {@code false}, so this is never double-counted against a
     * genuine quality failure.
     */
    @Query("""
            SELECT COUNT(DISTINCT r.evaluationRunId)
            FROM EvaluationResult r
            WHERE r.evaluationJobId = :jobId
            AND r.evaluationRunId NOT IN (
                SELECT r2.evaluationRunId FROM EvaluationResult r2
                WHERE r2.evaluationJobId = :jobId
                AND r2.executionStatus = com.broksforge.modules.evaluation.domain.MetricExecutionStatus.COMPLETED
            )
            """)
    long countSkippedRuns(@Param("jobId") UUID jobId);
}
