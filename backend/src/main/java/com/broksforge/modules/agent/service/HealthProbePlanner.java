package com.broksforge.modules.agent.service;

import com.broksforge.modules.agent.domain.AgentFramework;
import com.broksforge.modules.agent.domain.HealthProbeStrategy;
import com.broksforge.modules.agent.domain.LlmProvider;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Pure, provider-aware planner that decides <em>how</em> to probe an agent's
 * health/connectivity, because different targets expose validation differently:
 *
 * <ul>
 *   <li><b>Spring Boot / Spring AI</b> → {@code GET {base}/actuator/health}</li>
 *   <li><b>FastAPI / LangGraph / LangChain / CrewAI / …</b> → {@code GET {base}/health}</li>
 *   <li><b>LLM-provider endpoints</b> (Groq / OpenAI / OpenRouter / Mistral / DeepSeek / Ollama /
 *       Azure OpenAI / Anthropic / Gemini) → {@code GET} the provider's <b>models list</b>, the
 *       officially-supported, token-free way to validate reachability + API key. The models URL is
 *       derived from the invocation URL by the OpenAI-compatible convention
 *       ({@code …/chat/completions} → {@code …/models}; Anthropic {@code …/messages} → {@code …/models};
 *       Gemini {@code …/models/{model}:generateContent} → {@code …/models}).</li>
 *   <li><b>anything else</b> → a plain {@code GET} of the registered endpoint (historical behaviour)</li>
 * </ul>
 *
 * <p>An endpoint that already points at a health path (ends with {@code /health} or
 * {@code /actuator/health}) is honoured verbatim. The provider is taken from the agent version when
 * known and otherwise <b>detected from the endpoint host</b>, so provider-aware probing works even
 * for agents registered as {@code CUSTOM_REST} with no version. The planner is pure (no I/O) and safe
 * on any input — a malformed URL falls back to a verbatim GET.</p>
 */
public final class HealthProbePlanner {

    /** Minimal body for a provider completion probe (last-resort POST fallback). */
    static final String COMPLETION_PROBE_BODY = "{\"input\":\"ping\",\"parameters\":{\"max_tokens\":1}}";

    private static final Set<LlmProvider> HOSTED_LLM_PROVIDERS = Set.of(
            LlmProvider.OPENAI, LlmProvider.ANTHROPIC, LlmProvider.GROQ, LlmProvider.GOOGLE_GEMINI,
            LlmProvider.OPENROUTER, LlmProvider.DEEPSEEK, LlmProvider.MISTRAL, LlmProvider.COHERE,
            LlmProvider.AZURE_OPENAI, LlmProvider.OLLAMA);

    private HealthProbePlanner() {
    }

    /** A single planned probe: which HTTP method, at which URL, with which (optional) body. */
    public record ProbePlan(HttpMethod method, String url, String body, HealthProbeStrategy strategy) {
    }

    public static ProbePlan plan(String endpointUrl, AgentFramework framework, LlmProvider provider) {
        String path;
        String base;
        String host;
        try {
            URI uri = URI.create(endpointUrl);
            path = uri.getPath() == null ? "" : uri.getPath();
            base = uri.getScheme() + "://" + uri.getAuthority();
            host = uri.getHost() == null ? "" : uri.getHost();
        } catch (RuntimeException e) {
            // Malformed URL (should not happen post-validation): probe it verbatim.
            return new ProbePlan(HttpMethod.GET, endpointUrl, null, HealthProbeStrategy.GET_ROOT);
        }

        // 1. Respect an endpoint that already targets a health path.
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith("/actuator/health")) {
            return new ProbePlan(HttpMethod.GET, endpointUrl, null, HealthProbeStrategy.GET_ACTUATOR_HEALTH);
        }
        if (lowerPath.endsWith("/health") || lowerPath.endsWith("/healthz")) {
            return new ProbePlan(HttpMethod.GET, endpointUrl, null, HealthProbeStrategy.GET_HEALTH);
        }

        // 2. Self-hosted frameworks expose a dedicated health endpoint at the server root.
        if (framework == AgentFramework.SPRING_AI) {
            return new ProbePlan(HttpMethod.GET, base + "/actuator/health", null,
                    HealthProbeStrategy.GET_ACTUATOR_HEALTH);
        }
        if (framework == AgentFramework.LANGGRAPH || framework == AgentFramework.LANGCHAIN
                || framework == AgentFramework.CREWAI || framework == AgentFramework.AUTOGEN
                || framework == AgentFramework.PYDANTIC_AI || framework == AgentFramework.SEMANTIC_KERNEL
                || framework == AgentFramework.LLAMA_INDEX) {
            return new ProbePlan(HttpMethod.GET, base + "/health", null, HealthProbeStrategy.GET_HEALTH);
        }

        // 3. LLM-provider endpoint (CUSTOM_REST / OTHER / null framework): validate via the models list.
        LlmProvider effectiveProvider = provider != null ? provider : detectProvider(host);
        String modelsUrl = deriveModelsUrl(base, path);
        if (modelsUrl != null) {
            return new ProbePlan(HttpMethod.GET, modelsUrl, null, HealthProbeStrategy.GET_MODELS);
        }
        if (effectiveProvider == LlmProvider.OLLAMA) {
            // Ollama has no OpenAI-compatible /v1/models guarantee on its native route; its own
            // model listing endpoint is /api/tags, regardless of the registered path shape.
            return new ProbePlan(HttpMethod.GET, base + "/api/tags", null, HealthProbeStrategy.GET_MODELS);
        }
        if (effectiveProvider != null && HOSTED_LLM_PROVIDERS.contains(effectiveProvider)) {
            // Known provider host but an unrecognised path: fall back to the OpenAI-compatible default.
            return new ProbePlan(HttpMethod.GET, base + "/v1/models", null, HealthProbeStrategy.GET_MODELS);
        }

        // 4. Generic / unknown target.
        return new ProbePlan(HttpMethod.GET, endpointUrl, null, HealthProbeStrategy.GET_ROOT);
    }

    /**
     * Resolves the effective {@link LlmProvider} for {@code endpointUrl}: the explicit
     * {@code provider} when known, otherwise a best-effort detection from the endpoint's host.
     * Shared by callers that need to know "is this really Ollama?" for reasons beyond probe
     * planning (e.g. {@link com.broksforge.common.security.OutboundUrlGuard}'s narrow
     * trusted-local-Ollama bypass) without duplicating the detection logic.
     */
    public static LlmProvider effectiveProvider(String endpointUrl, LlmProvider provider) {
        if (provider != null) {
            return provider;
        }
        try {
            return detectProvider(URI.create(endpointUrl).getHost());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Detects a hosted LLM provider from the endpoint host, so provider-aware probing works even when
     * the agent has no version. Descriptive only — the actual probe URL is derived from the path.
     */
    public static LlmProvider detectProvider(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.contains("groq.com")) return LlmProvider.GROQ;
        if (h.endsWith("openai.azure.com") || h.contains("azure")) return LlmProvider.AZURE_OPENAI;
        if (h.contains("api.openai.com")) return LlmProvider.OPENAI;
        if (h.contains("anthropic.com")) return LlmProvider.ANTHROPIC;
        if (h.contains("openrouter.ai")) return LlmProvider.OPENROUTER;
        if (h.contains("deepseek.com")) return LlmProvider.DEEPSEEK;
        if (h.contains("mistral.ai")) return LlmProvider.MISTRAL;
        if (h.contains("cohere.")) return LlmProvider.COHERE;
        if (h.contains("generativelanguage.googleapis.com")) return LlmProvider.GOOGLE_GEMINI;
        if (h.contains("googleapis.com")) return LlmProvider.GOOGLE_VERTEX;
        if (h.contains("localhost") || h.equals("127.0.0.1") || h.contains("ollama")) return LlmProvider.OLLAMA;
        return null;
    }

    /**
     * Maps a detected {@link LlmProvider} to the short, lowercase key used to look up
     * provider-specific configuration (e.g. {@code broksforge.providers.default-max-tokens.<key>}).
     * Both Gemini and Vertex map to {@code "google"}, and Azure OpenAI to {@code "azure"}, since
     * configuration is keyed by vendor rather than by API variant.
     */
    public static String providerConfigKey(LlmProvider provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case GOOGLE_GEMINI, GOOGLE_VERTEX -> "google";
            case AZURE_OPENAI -> "azure";
            default -> provider.name().toLowerCase(Locale.ROOT);
        };
    }

    /**
     * True when {@code endpointUrl} matches a hosted LLM provider's request shape whose wire
     * protocol requires a {@code model} field in the body: OpenAI-compatible chat-completions
     * (OpenAI, Groq, OpenRouter, Mistral, DeepSeek, Azure OpenAI, ...), Anthropic's
     * {@code /messages}, or Ollama's native {@code /api/chat}. Every matching {@code ProviderAdapter}
     * (see {@code OpenAiCompatibleAdapter}/{@code AnthropicAdapter}/{@code OllamaAdapter}) only
     * adds {@code "model"} to the payload when one is resolved, so an unresolved model is silently
     * dropped rather than rejected client-side — this is what lets both {@code AgentEndpointInvoker}
     * (fail fast before the HTTP call) and evaluation-job creation (require a resolvable model up
     * front) catch the gap instead of discovering it from the provider's own HTTP 400 mid-run.
     * Google AI Studio is deliberately excluded — its adapter embeds the model in the URL path, not
     * the body.
     */
    public static boolean requiresModelField(String endpointUrl) {
        try {
            String path = URI.create(endpointUrl).getPath();
            if (path == null) {
                return false;
            }
            String lower = path.toLowerCase(Locale.ROOT);
            return lower.endsWith("/chat/completions") || lower.endsWith("/messages") || lower.endsWith("/api/chat");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Provider/vendor names users mistakenly type into a model field instead of a real model id. */
    private static final Set<String> PROVIDER_NAME_LOOKALIKES = Set.of(
            "openai", "groq", "openrouter", "anthropic", "claude", "gemini", "google", "googlegemini",
            "mistral", "mistralai", "deepseek", "cohere", "azure", "azureopenai", "ollama", "vertex",
            "vertexai", "huggingface", "together", "togetherai", "perplexity", "xai", "grok");

    /**
     * True when {@code model} is a bare provider/vendor name ("Groq", "OpenAI", "Claude", ...)
     * rather than an actual model identifier ("llama-3.3-70b-versatile", "gpt-4o-mini", ...).
     * Purely a spelling heuristic — it cannot know whether a syntactically model-shaped string is
     * one the provider actually serves, since that requires calling the provider.
     */
    public static boolean looksLikeProviderName(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String normalized = model.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", "");
        return PROVIDER_NAME_LOOKALIKES.contains(normalized);
    }

    /**
     * Derives the provider's models-list URL from an LLM invocation URL, using the well-known
     * conventions. Returns {@code null} if the path is not a recognised LLM invocation path.
     */
    static String deriveModelsUrl(String base, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        // Ollama's native chat route: .../api/chat -> .../api/tags (never /v1/models — that's the
        // OpenAI-compatibility shim, not the native API this platform's Ollama adapter speaks).
        if (lower.endsWith("/api/chat")) {
            return base + path.substring(0, path.length() - "/api/chat".length()) + "/api/tags";
        }
        // OpenAI-compatible: .../chat/completions -> .../models (OpenAI, Groq, OpenRouter, Mistral,
        // DeepSeek, Together, Ollama-OpenAI, Azure OpenAI, …).
        if (lower.endsWith("/chat/completions")) {
            return base + path.substring(0, path.length() - "/chat/completions".length()) + "/models";
        }
        // Legacy OpenAI text completions and embeddings live beside /models too.
        if (lower.endsWith("/completions")) {
            return base + path.substring(0, path.length() - "/completions".length()) + "/models";
        }
        // Anthropic Messages API: .../v1/messages -> .../v1/models
        if (lower.endsWith("/messages")) {
            return base + path.substring(0, path.length() - "/messages".length()) + "/models";
        }
        // Google Gemini: .../v1beta/models/{model}:generateContent -> .../v1beta/models
        int modelsSegment = lower.indexOf("/models/");
        if (modelsSegment >= 0 && (lower.endsWith(":generatecontent") || lower.endsWith(":streamgeneratecontent"))) {
            return base + path.substring(0, modelsSegment) + "/models";
        }
        return null;
    }
}
