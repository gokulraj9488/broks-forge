package com.broksforge.modules.evaluation.repository;

import com.broksforge.modules.evaluation.domain.EvaluationResult;
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
            WHERE r.evaluationJobId = :jobId
            GROUP BY r.metricType
            """)
    List<MetricFailureTally> tallyByMetric(@Param("jobId") UUID jobId);
}
