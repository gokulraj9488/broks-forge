package com.broksforge.modules.evaluation.repository;

import java.math.BigDecimal;

/**
 * Database-side aggregate over evaluation runs for analytics. Counts/sums are
 * {@code null} when the window contains no runs; callers coalesce.
 */
public record EvaluationRunAggregate(
        Long runCount,
        Long passedCount,
        Double avgLatencyMs,
        Long totalTokens,
        BigDecimal totalCost
) {
}
