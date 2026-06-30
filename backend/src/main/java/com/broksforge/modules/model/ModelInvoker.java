package com.broksforge.modules.model;

/**
 * Service Provider Interface for executing a model invocation. New providers
 * (OpenAI, Anthropic, Groq, Ollama, Gemini, OpenRouter, DeepSeek, ...) are added by
 * implementing this interface and registering a Spring bean — no change to the
 * dispatcher or any caller is required (see ADR 0006). Each invoker decides whether
 * it can serve a given request via {@link #supports(ModelInvocationRequest)}.
 */
public interface ModelInvoker {

    /** A stable identifier for logs and diagnostics, e.g. {@code "agent-endpoint"}. */
    String name();

    /** Whether this invoker can handle the given request. */
    boolean supports(ModelInvocationRequest request);

    /** Executes the invocation. Implementations must not throw for upstream errors; they return a failure result. */
    ModelInvocationResult invoke(ModelInvocationRequest request);
}
