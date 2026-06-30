package com.broksforge.modules.evaluation.service;

import java.math.BigDecimal;

/**
 * Published, project-scoped roll-up of evaluation activity over a time window,
 * consumed by the analytics and dashboard modules.
 */
public record EvaluationAnalyticsSummary(
        long jobCount,
        long runCount,
        long passedCount,
        double passRate,
        Double avgLatencyMs,
        long totalTokens,
        BigDecimal totalCost
) {
}
