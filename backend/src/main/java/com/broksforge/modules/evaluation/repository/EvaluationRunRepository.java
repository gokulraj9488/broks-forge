package com.broksforge.modules.evaluation.repository;

import com.broksforge.modules.evaluation.domain.EvaluationRun;
import com.broksforge.modules.evaluation.domain.EvaluationRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, UUID> {

    Page<EvaluationRun> findByEvaluationJobIdOrderBySequenceAsc(UUID evaluationJobId, Pageable pageable);

    Optional<EvaluationRun> findByIdAndEvaluationJobId(UUID id, UUID evaluationJobId);

    List<EvaluationRun> findByEvaluationJobIdAndStatusOrderBySequenceAsc(
            UUID evaluationJobId, EvaluationRunStatus status, Pageable pageable);

    long countByEvaluationJobId(UUID evaluationJobId);

    /**
     * Project-scoped aggregate over runs in a time window. Project scoping is applied
     * via a subquery on jobs, so the runs table stays free of a project column.
     */
    @Query("""
            SELECT new com.broksforge.modules.evaluation.repository.EvaluationRunAggregate(
                COUNT(r),
                SUM(CASE WHEN r.passed = true THEN 1L ELSE 0L END),
                AVG(r.latencyMs),
                SUM(r.totalTokens),
                SUM(r.cost))
            FROM EvaluationRun r
            WHERE r.organizationId = :organizationId
              AND r.createdAt >= :from
              AND r.evaluationJobId IN (
                  SELECT j.id FROM EvaluationJob j
                  WHERE j.projectId = :projectId AND j.deleted = false)
            """)
    EvaluationRunAggregate aggregate(@Param("organizationId") UUID organizationId,
                                     @Param("projectId") UUID projectId,
                                     @Param("from") Instant from);

    /**
     * Daily time series of run count, average latency, token and cost totals.
     * Returned as raw rows ({@code [bucket, count, avgLatency, totalTokens, totalCost]})
     * and reshaped by the analytics service.
     */
    @Query(value = """
            SELECT date_trunc('day', r.created_at) AS bucket,
                   COUNT(*)                        AS run_count,
                   AVG(r.latency_ms)               AS avg_latency,
                   COALESCE(SUM(r.total_tokens), 0) AS total_tokens,
                   COALESCE(SUM(r.cost), 0)         AS total_cost
            FROM evaluation_runs r
            WHERE r.organization_id = :organizationId
              AND r.created_at >= :from
              AND r.evaluation_job_id IN (
                  SELECT j.id FROM evaluation_jobs j
                  WHERE j.project_id = :projectId AND j.deleted = false)
            GROUP BY bucket
            ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> dailyTrend(@Param("organizationId") UUID organizationId,
                              @Param("projectId") UUID projectId,
                              @Param("from") Instant from);
}
