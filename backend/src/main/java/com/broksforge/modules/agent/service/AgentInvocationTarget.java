package com.broksforge.modules.agent.service;

import java.util.Map;
import java.util.UUID;

/**
 * The published, id-referenced view of an agent needed to invoke it: its endpoint
 * and resolved authentication headers. Returned by {@link AgentInvocationService} so
 * sibling modules (evaluation, benchmarking) never touch the agent's entities or
 * credential ciphertext directly.
 *
 * @param agentId         the agent invoked
 * @param activeVersionId the agent's current active version (may be {@code null})
 * @param endpointUrl     the agent's endpoint URL
 * @param headers         resolved auth headers (decrypted); must never be logged
 */
public record AgentInvocationTarget(
        UUID agentId,
        UUID activeVersionId,
        String endpointUrl,
        Map<String, String> headers
) {
}
