package com.broksforge.modules.evaluation.service.metric;

import java.math.BigDecimal;

/**
 * The data a metric is scored against: the rendered input/prompt, the produced output,
 * the (optional) expected output, and the invocation telemetry. Immutable and
 * self-contained so the metric engine has no dependency on persistence.
 *
 * <p>{@code input} is the fully rendered prompt (template + dataset input) sent to the
 * model — added for judge-style metrics (LLM Judge, Hallucination Detection, Citation
 * Verification) that need the original request context, not just the response. Purely
 * additive: every pre-existing metric ignores it.</p>
 */
public record MetricContext(
        String input,
        String output,
        String expectedOutput,
        Long latencyMs,
        Integer totalTokens,
        BigDecimal cost
) {
}
