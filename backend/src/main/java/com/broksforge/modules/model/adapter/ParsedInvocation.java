package com.broksforge.modules.model.adapter;

import java.math.BigDecimal;

/**
 * A provider adapter's parse of a successful (2xx) response: output text plus token/cost
 * accounting.
 *
 * @param blockedReason non-null when the provider returned 2xx but generation was blocked/refused
 *                       (e.g. Google AI Studio's safety filter finish reason with no candidate
 *                       content) — the caller ({@code AgentEndpointInvoker}) treats this as a
 *                       failure using this as the error message, rather than recording a
 *                       misleadingly "successful" empty output. {@code null} for a normal result.
 */
public record ParsedInvocation(
        String output,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        BigDecimal cost,
        String blockedReason
) {
}
