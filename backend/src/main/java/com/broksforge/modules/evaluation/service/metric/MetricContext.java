package com.broksforge.modules.evaluation.service.metric;

import java.math.BigDecimal;

/**
 * The data a metric is scored against: the produced output, the (optional) expected
 * output, and the invocation telemetry. Immutable and self-contained so the metric
 * engine has no dependency on persistence.
 */
public record MetricContext(
        String output,
        String expectedOutput,
        Long latencyMs,
        Integer totalTokens,
        BigDecimal cost
) {
}
