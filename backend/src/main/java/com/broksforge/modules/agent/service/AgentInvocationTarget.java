package com.broksforge.modules.agent.service;

import java.util.Map;
import java.util.UUID;

/**
 * The published, id-referenced view of an agent needed to invoke it: its endpoint,
 * resolved authentication headers, and the active version's declared model (if any).
 * Returned by {@link AgentInvocationService} so sibling modules (evaluation,
 * benchmarking) never touch the agent's entities or credential ciphertext directly.
 *
 * @param agentId             the agent invoked
 * @param activeVersionId     the agent's current active version (may be {@code null})
 * @param endpointUrl         the agent's endpoint URL
 * @param headers             resolved auth headers (decrypted); must never be logged
 * @param model               the active version's model, or {@code null} if the agent has no
 *                            active version or none is set on it — second tier of the model
 *                            resolution precedence (job override, then this, then
 *                            {@code providerDefaultModel})
 * @param providerDefaultModel the linked Provider's configured default model, or {@code null} if
 *                            the agent has no linked provider or the provider has none set —
 *                            third and final tier of the model resolution precedence
 */
public record AgentInvocationTarget(
        UUID agentId,
        UUID activeVersionId,
        String endpointUrl,
        Map<String, String> headers,
        String model,
        String providerDefaultModel
) {
    /** The effective model after applying the full precedence: this target's own fallbacks only. */
    public String fallbackModel() {
        return model != null ? model : providerDefaultModel;
    }
}
