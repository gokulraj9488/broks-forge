package com.broksforge.modules.evaluation.service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One day's evaluation telemetry, used to build cost/latency/usage trend charts.
 */
public record EvaluationTrendPoint(
        Instant date,
        long runCount,
        Double avgLatencyMs,
        long totalTokens,
        BigDecimal totalCost
) {
}
