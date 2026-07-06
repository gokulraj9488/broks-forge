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
        if (effectiveProvider != null && HOSTED_LLM_PROVIDERS.contains(effectiveProvider)) {
            // Known provider host but an unrecognised path: fall back to the OpenAI-compatible default.
            return new ProbePlan(HttpMethod.GET, base + "/v1/models", null, HealthProbeStrategy.GET_MODELS);
        }

        // 4. Generic / unknown target.
        return new ProbePlan(HttpMethod.GET, endpointUrl, null, HealthProbeStrategy.GET_ROOT);
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
     * Derives the provider's models-list URL from an LLM invocation URL, using the well-known
     * conventions. Returns {@code null} if the path is not a recognised LLM invocation path.
     */
    static String deriveModelsUrl(String base, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String lower = path.toLowerCase(Locale.ROOT);
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
