package com.broksforge.modules.evaluation.domain;

import java.util.Locale;

/**
 * Whether a metric actually ran to completion, distinct from whether it passed. A network-call
 * metric (Semantic Similarity, LLM Judge, Hallucination Detection, Citation Verification) can
 * fail before it ever produces a score — a 401 from the judge provider is not "a low judge
 * score", it's the judge never having run. Only {@link #COMPLETED} outcomes carry a meaningful
 * {@code passed}/{@code score}; every other value means the metric's {@code passed}/{@code score}
 * are {@code null} and the failure reason is this status instead.
 */
public enum MetricExecutionStatus {

    /** The metric ran and produced a real pass/fail + score. */
    COMPLETED,
    /** The provider rejected the call as unauthenticated/unauthorized (HTTP 401/403). */
    AUTHENTICATION_ERROR,
    /** The provider couldn't be reached or used at all — disabled, unsupported endpoint shape, network/SSRF block, or HTTP 5xx. */
    PROVIDER_UNAVAILABLE,
    /** The provider rejected the call for rate limiting (HTTP 429). */
    RATE_LIMITED,
    /** The requested model id doesn't exist for this provider (HTTP 404). */
    MODEL_NOT_FOUND,
    /** The call didn't complete within the configured timeout. */
    TIMEOUT,
    /** Any other execution failure — missing/invalid config, unparseable provider response, an unexpected exception. */
    INFRASTRUCTURE_ERROR;

    /**
     * Classifies a failed provider call (embedding or judge) from whatever's already captured
     * about it — an HTTP status when the provider actually responded, and/or the free-text error
     * message otherwise (timeouts, connection failures, network-policy blocks never reach the
     * HTTP-status branch). Mirrors the same status/text heuristics {@code RootCauseEngine} already
     * applies to agent-invocation runs, so a 401 means the same thing everywhere in this system.
     * Never returns {@code null} — anything unrecognised falls back to {@link #INFRASTRUCTURE_ERROR}
     * so a real score is never fabricated for an unclassified failure.
     */
    public static MetricExecutionStatus classify(Integer httpStatus, String message) {
        String msg = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return TIMEOUT;
        }
        if (httpStatus != null) {
            if (httpStatus == 401 || httpStatus == 403) {
                return AUTHENTICATION_ERROR;
            }
            if (httpStatus == 404) {
                return MODEL_NOT_FOUND;
            }
            if (httpStatus == 429) {
                return RATE_LIMITED;
            }
            if (httpStatus >= 500) {
                return PROVIDER_UNAVAILABLE;
            }
        }
        if (msg.contains("not found") || msg.contains("disabled") || msg.contains("blocked by network policy")
                || msg.contains("isn't supported") || msg.contains("not supported")
                || msg.contains("connection") || msg.contains("refused") || msg.contains("unreachable")
                || msg.contains("unknown host") || msg.contains("dns") || msg.contains("no route to host")) {
            return PROVIDER_UNAVAILABLE;
        }
        return INFRASTRUCTURE_ERROR;
    }
}
