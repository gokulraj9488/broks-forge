package com.broksforge.modules.model;

import java.math.BigDecimal;

/**
 * The outcome of a single model invocation. Token usage and cost are populated only
 * when the upstream reports them (the platform never fabricates these figures — see
 * PERFORMANCE_GUIDE.md). A non-successful result carries a human-readable
 * {@code error} reason rather than throwing, so an evaluation can record per-row
 * failures and continue.
 */
public record ModelInvocationResult(
        boolean success,
        String output,
        Integer httpStatus,
        long latencyMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        BigDecimal cost,
        String error
) {

    public static ModelInvocationResult failure(Integer httpStatus, long latencyMs, String error) {
        return new ModelInvocationResult(false, null, httpStatus, latencyMs, null, null, null, null, error);
    }
}
