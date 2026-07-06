package com.broksforge.modules.agent.domain;

/**
 * How the platform probes an agent's health, chosen automatically from the
 * agent's framework and provider (see {@code HealthProbePlanner}). Stored on each
 * {@code AgentHealthCheck} so the history records exactly how the check was made.
 */
public enum HealthProbeStrategy {

    /** Plain GET against the registered endpoint URL (generic / unknown targets). */
    GET_ROOT("GET endpoint"),

    /** GET {base}/health — FastAPI / LangGraph / LangChain-style servers. */
    GET_HEALTH("GET /health"),

    /** GET {base}/actuator/health — Spring Boot / Spring AI services. */
    GET_ACTUATOR_HEALTH("GET /actuator/health"),

    /**
     * GET the provider's models list (e.g. {@code /openai/v1/models}) — the officially
     * supported, token-free validation for OpenAI-compatible providers
     * (Groq / OpenAI / OpenRouter / Mistral / DeepSeek / Ollama / Azure OpenAI) and for
     * Anthropic / Gemini. Confirms both reachability and that the API key is accepted.
     */
    GET_MODELS("GET /models"),

    /**
     * Tiny POST completion — retained for history and as a last resort for
     * LLM endpoints whose models list cannot be derived.
     */
    POST_COMPLETION("POST completion");

    private final String label;

    HealthProbeStrategy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
