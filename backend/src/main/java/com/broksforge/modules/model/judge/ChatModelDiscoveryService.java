package com.broksforge.modules.model.judge;

import com.broksforge.common.security.CredentialEncryptionService;
import com.broksforge.common.security.OutboundUrlGuard;
import com.broksforge.config.properties.ModelInvocationProperties;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.model.adapter.ProviderAdapter;
import com.broksforge.modules.model.adapter.ProviderAdapterRegistry;
import com.broksforge.modules.provider.domain.Provider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lists chat/judge-capable models actually available for a stored {@link Provider}, so the LLM
 * Judge/Hallucination Detection/Citation Verification metric editors offer real model ids instead
 * of a free-text guess. Unlike embeddings (which only OpenAI/Google/Ollama support), every
 * provider this platform integrates has a chat-completions capable API, so all six are queried
 * live: OpenAI/Groq/OpenRouter's shared {@code /models} shape, Google AI Studio's {@code /models}
 * (filtered to {@code generateContent} support), Ollama's {@code /api/tags}, and Anthropic's own
 * {@code /v1/models} listing endpoint.
 */
@Slf4j
@Service
public class ChatModelDiscoveryService {

    private static final Pattern GOOGLE_MODEL_SEGMENT =
            Pattern.compile("(.*/models/)([^/:]+)(:generateContent|:streamGenerateContent)");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Ollama's /api/tags has no explicit chat/embedding flag — exclude names that look embedding-only. */
    private static final List<String> OLLAMA_EMBEDDING_HINTS =
            List.of("embed", "bge", "gte", "minilm", "e5-");

    private final CredentialEncryptionService encryptionService;
    private final ProviderAdapterRegistry adapterRegistry;
    private final OutboundUrlGuard urlGuard;
    private final ModelInvocationProperties properties;
    private final RestClient restClient;

    public ChatModelDiscoveryService(CredentialEncryptionService encryptionService,
                                     ProviderAdapterRegistry adapterRegistry,
                                     OutboundUrlGuard urlGuard,
                                     ModelInvocationProperties properties) {
        this.encryptionService = encryptionService;
        this.adapterRegistry = adapterRegistry;
        this.urlGuard = urlGuard;
        this.properties = properties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.toIntExact(properties.timeoutMs());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public record Result(boolean supported, List<String> models, String message) {
        static Result unsupported(String message) {
            return new Result(false, List.of(), message);
        }

        static Result of(List<String> models) {
            return new Result(true, models, null);
        }

        static Result failed(String message) {
            return new Result(true, List.of(), message);
        }
    }

    public Result listChatModels(Provider provider) {
        return switch (provider.getType()) {
            case OPENAI, GROQ, OPENROUTER -> listOpenAiCompatible(provider);
            case GOOGLE_GEMINI -> listGoogle(provider);
            case OLLAMA -> listOllama(provider);
            case ANTHROPIC -> listAnthropic(provider);
            default -> Result.unsupported("Model discovery isn't available for this provider type — "
                    + "enter the model id manually.");
        };
    }

    // ----------------------------------------------------------------------
    // OpenAI-compatible (OpenAI, Groq, OpenRouter)
    // ----------------------------------------------------------------------

    private Result listOpenAiCompatible(Provider provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || !baseUrl.endsWith("/chat/completions")) {
            return Result.unsupported("This provider's endpoint isn't the expected OpenAI-compatible shape.");
        }
        String listUrl = baseUrl.substring(0, baseUrl.length() - "/chat/completions".length()) + "/models";
        try {
            JsonNode root = get(listUrl, provider);
            JsonNode data = root == null ? null : root.get("data");
            if (data == null || !data.isArray()) {
                return Result.failed("Provider's models list had an unexpected shape.");
            }
            List<String> models = new ArrayList<>();
            for (JsonNode entry : data) {
                String id = entry.hasNonNull("id") ? entry.get("id").asText() : null;
                if (id != null && !looksLikeNonChatModel(id)) {
                    models.add(id);
                }
            }
            return Result.of(models);
        } catch (Exception e) {
            return Result.failed("Couldn't list models: " + truncate(e.getMessage()));
        }
    }

    private boolean looksLikeNonChatModel(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.contains("embed") || lower.contains("whisper") || lower.contains("tts")
                || lower.contains("dall-e") || lower.contains("moderation") || lower.contains("image");
    }

    // ----------------------------------------------------------------------
    // Google AI Studio
    // ----------------------------------------------------------------------

    private Result listGoogle(Provider provider) {
        String baseUrl = provider.getBaseUrl();
        Matcher matcher = GOOGLE_MODEL_SEGMENT.matcher(baseUrl == null ? "" : baseUrl);
        if (!matcher.find()) {
            return Result.unsupported("This provider's endpoint isn't the expected Google AI Studio shape.");
        }
        String listUrl = matcher.group(1).substring(0, matcher.group(1).length() - 1);
        try {
            JsonNode root = get(listUrl, provider);
            JsonNode modelsNode = root == null ? null : root.get("models");
            if (modelsNode == null || !modelsNode.isArray()) {
                return Result.failed("Provider's models list had an unexpected shape.");
            }
            List<String> models = new ArrayList<>();
            for (JsonNode entry : modelsNode) {
                JsonNode methods = entry.get("supportedGenerationMethods");
                boolean chats = methods != null && methods.isArray() && containsText(methods, "generateContent");
                if (!chats || !entry.hasNonNull("name")) {
                    continue;
                }
                String name = entry.get("name").asText();
                models.add(name.startsWith("models/") ? name.substring("models/".length()) : name);
            }
            return Result.of(models);
        } catch (Exception e) {
            return Result.failed("Couldn't list models: " + truncate(e.getMessage()));
        }
    }

    private boolean containsText(JsonNode array, String value) {
        for (JsonNode item : array) {
            if (item.isTextual() && value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------------
    // Ollama
    // ----------------------------------------------------------------------

    private Result listOllama(Provider provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || !baseUrl.endsWith("/api/chat")) {
            return Result.unsupported("This provider's endpoint isn't the expected Ollama shape.");
        }
        String listUrl = baseUrl.substring(0, baseUrl.length() - "/api/chat".length()) + "/api/tags";
        try {
            JsonNode root = get(listUrl, provider);
            JsonNode modelsNode = root == null ? null : root.get("models");
            if (modelsNode == null || !modelsNode.isArray()) {
                return Result.failed("Provider's models list had an unexpected shape.");
            }
            List<String> models = new ArrayList<>();
            for (JsonNode entry : modelsNode) {
                String name = entry.hasNonNull("name") ? entry.get("name").asText() : null;
                if (name == null) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (OLLAMA_EMBEDDING_HINTS.stream().noneMatch(lower::contains)) {
                    models.add(name);
                }
            }
            return Result.of(models);
        } catch (Exception e) {
            return Result.failed("Couldn't list models: " + truncate(e.getMessage()));
        }
    }

    // ----------------------------------------------------------------------
    // Anthropic
    // ----------------------------------------------------------------------

    private Result listAnthropic(Provider provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || !baseUrl.endsWith("/messages")) {
            return Result.unsupported("This provider's endpoint isn't the expected Anthropic shape.");
        }
        String listUrl = baseUrl.substring(0, baseUrl.length() - "/messages".length()) + "/models";
        try {
            JsonNode root = get(listUrl, provider);
            JsonNode data = root == null ? null : root.get("data");
            if (data == null || !data.isArray()) {
                return Result.failed("Provider's models list had an unexpected shape.");
            }
            List<String> models = new ArrayList<>();
            for (JsonNode entry : data) {
                if (entry.hasNonNull("id")) {
                    models.add(entry.get("id").asText());
                }
            }
            return Result.of(models);
        } catch (Exception e) {
            return Result.failed("Couldn't list models: " + truncate(e.getMessage()));
        }
    }

    // ----------------------------------------------------------------------
    // Shared HTTP
    // ----------------------------------------------------------------------

    private JsonNode get(String url, Provider provider) throws Exception {
        boolean trustedOllama = provider.getType() == LlmProvider.OLLAMA;
        OutboundUrlGuard.Decision decision = urlGuard.check(url, properties.allowPrivateTargets(), trustedOllama);
        if (!decision.allowed()) {
            throw new IllegalStateException("Blocked by network policy: " + decision.reason());
        }
        String apiKey = provider.getEncryptedApiKey() != null
                ? encryptionService.decrypt(provider.getEncryptedApiKey()) : null;
        ProviderAdapter adapter = adapterRegistry.resolve(provider.getBaseUrl());
        Map<String, String> authHeaders = adapter != null
                ? adapter.buildAuthHeaders(apiKey)
                : (apiKey == null || apiKey.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + apiKey));

        String body = restClient.get()
                .uri(URI.create(url))
                .headers(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
                    authHeaders.forEach(headers::set);
                    provider.getDefaultHeaders().forEach((k, v) -> headers.set(k, String.valueOf(v)));
                })
                .exchange((req, res) -> {
                    if (!res.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("HTTP " + res.getStatusCode().value());
                    }
                    return res.bodyTo(String.class);
                });
        return body == null ? null : OBJECT_MAPPER.readTree(body);
    }

    private String truncate(String value) {
        if (value == null) {
            return "(no message)";
        }
        return value.length() <= 300 ? value : value.substring(0, 300);
    }
}
