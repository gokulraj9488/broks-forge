package com.broksforge.modules.model;

import com.broksforge.modules.agent.domain.LlmProvider;

import java.util.Map;
import java.util.UUID;

/**
 * A single, provider-agnostic model invocation. The execution target is either a
 * concrete HTTP {@link ModelTarget} (the registered-agent path used today) or, for
 * a future provider-direct invoker, identified by {@code provider} + {@code model}.
 *
 * @param organizationId tenant scope (for logging/observability)
 * @param projectId      project scope
 * @param provider       the LLM provider vocabulary value, or {@code null} for an opaque agent
 * @param model          the model identifier, or {@code null}
 * @param input          the rendered input/prompt to send
 * @param parameters     optional generation parameters (temperature, max tokens, ...)
 * @param target         the HTTP target, or {@code null} for a provider-direct invoker
 */
public record ModelInvocationRequest(
        UUID organizationId,
        UUID projectId,
        LlmProvider provider,
        String model,
        String input,
        Map<String, Object> parameters,
        ModelTarget target
) {
    public ModelInvocationRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
