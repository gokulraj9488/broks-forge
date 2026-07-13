package com.broksforge.modules.model.judge;

import com.broksforge.common.security.CredentialEncryptionService;
import com.broksforge.common.security.OutboundUrlGuard;
import com.broksforge.config.properties.ModelInvocationProperties;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.model.adapter.ProviderAdapter;
import com.broksforge.modules.model.adapter.ProviderAdapterRegistry;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates an embedding vector for a piece of text via a stored {@link Provider}, for the
 * Semantic Similarity metric. Embeddings have a genuinely different wire shape from chat
 * completions (different endpoint, different request/response body), so — unlike judge calls,
 * which reuse {@code ProviderAdapter#buildPayload}/{@code parseSuccess} — this speaks the two
 * embedding shapes directly: OpenAI-compatible {@code POST .../embeddings} and Google AI
 * Studio's {@code :embedContent}. A provider whose base URL matches neither shape returns a
 * clear {@link EmbeddingResult#error(String)} rather than a guessed, likely-wrong request.
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final Pattern GOOGLE_MODEL_SEGMENT =
            Pattern.compile("(/models/)([^/:]+)(:generateContent|:streamGenerateContent)");

    private final ProviderRepository providerRepository;
    private final CredentialEncryptionService encryptionService;
    private final ProviderAdapterRegistry adapterRegistry;
    private final OutboundUrlGuard urlGuard;
    private final ModelInvocationProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public EmbeddingService(ProviderRepository providerRepository,
                            CredentialEncryptionService encryptionService,
                            ProviderAdapterRegistry adapterRegistry,
                            OutboundUrlGuard urlGuard,
                            ModelInvocationProperties properties,
                            ObjectMapper objectMapper) {
        this.providerRepository = providerRepository;
        this.encryptionService = encryptionService;
        this.adapterRegistry = adapterRegistry;
        this.urlGuard = urlGuard;
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.toIntExact(properties.timeoutMs());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public EmbeddingResult embed(UUID providerId, String model, String text) {
        if (providerId == null) {
            return EmbeddingResult.error("No embedding provider configured (params.providerId is required)");
        }
        Provider provider = providerRepository.findById(providerId).orElse(null);
        if (provider == null) {
            return EmbeddingResult.error("Embedding provider " + providerId + " not found");
        }
        if (!provider.isEnabled()) {
            return EmbeddingResult.error("Embedding provider '" + provider.getName() + "' is disabled");
        }
        String baseUrl = provider.getBaseUrl();
        String apiKey = provider.getEncryptedApiKey() != null ? encryptionService.decrypt(provider.getEncryptedApiKey()) : null;
        ProviderAdapter adapter = adapterRegistry.resolve(baseUrl);
        Map<String, String> authHeaders = adapter != null
                ? adapter.buildAuthHeaders(apiKey)
                : (apiKey == null || apiKey.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + apiKey));

        if (provider.getType() == LlmProvider.OLLAMA && baseUrl != null && baseUrl.endsWith("/api/chat")) {
            String embeddingModel = (model != null && !model.isBlank()) ? model : "nomic-embed-text";
            String url = baseUrl.substring(0, baseUrl.length() - "/api/chat".length()) + "/api/embed";
            return ollamaEmbed(url, embeddingModel, text, authHeaders, provider);
        }
        if (baseUrl != null && baseUrl.endsWith("/chat/completions")) {
            String embeddingModel = (model != null && !model.isBlank()) ? model : "text-embedding-3-small";
            String url = baseUrl.substring(0, baseUrl.length() - "/chat/completions".length()) + "/embeddings";
            return openAiCompatibleEmbed(url, embeddingModel, text, authHeaders, provider);
        }
        Matcher googleMatch = GOOGLE_MODEL_SEGMENT.matcher(baseUrl == null ? "" : baseUrl);
        if (googleMatch.find()) {
            String embeddingModel = (model != null && !model.isBlank()) ? model : "text-embedding-004";
            String url = googleMatch.replaceFirst("$1" + embeddingModel + ":embedContent");
            return googleEmbed(url, text, authHeaders, provider);
        }
        return EmbeddingResult.error("Semantic similarity isn't supported for this provider's endpoint shape yet "
                + "(supported: an OpenAI-compatible /chat/completions base URL, Google AI Studio, or Ollama's native /api/chat)");
    }

    private EmbeddingResult ollamaEmbed(String url, String model, String text,
                                        Map<String, String> authHeaders, Provider provider) {
        Map<String, Object> payload = Map.of("model", model, "input", text);
        try {
            RawResponse raw = post(url, payload, authHeaders, provider);
            if (!raw.status().is2xxSuccessful()) {
                return EmbeddingResult.error("Embedding call returned HTTP " + raw.status().value() + ": " + truncate(raw.body()),
                        raw.status().value());
            }
            JsonNode root = objectMapper.readTree(raw.body());
            JsonNode embeddings = root.get("embeddings");
            if (embeddings == null || !embeddings.isArray() || embeddings.isEmpty()) {
                return EmbeddingResult.error("Embedding response had no embeddings[0]: " + truncate(raw.body()));
            }
            return EmbeddingResult.of(toFloatArray(embeddings.get(0)));
        } catch (Exception e) {
            return EmbeddingResult.error("Embedding call failed: " + truncate(e.getMessage()));
        }
    }

    private EmbeddingResult openAiCompatibleEmbed(String url, String model, String text,
                                                  Map<String, String> authHeaders, Provider provider) {
        Map<String, Object> payload = Map.of("model", model, "input", text);
        try {
            RawResponse raw = post(url, payload, authHeaders, provider);
            if (!raw.status().is2xxSuccessful()) {
                return EmbeddingResult.error("Embedding call returned HTTP " + raw.status().value() + ": " + truncate(raw.body()),
                        raw.status().value());
            }
            JsonNode root = objectMapper.readTree(raw.body());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty() || !data.get(0).hasNonNull("embedding")) {
                return EmbeddingResult.error("Embedding response had no data[0].embedding: " + truncate(raw.body()));
            }
            return EmbeddingResult.of(toFloatArray(data.get(0).get("embedding")));
        } catch (Exception e) {
            return EmbeddingResult.error("Embedding call failed: " + truncate(e.getMessage()));
        }
    }

    private EmbeddingResult googleEmbed(String url, String text, Map<String, String> authHeaders, Provider provider) {
        Map<String, Object> payload = Map.of("content", Map.of("parts", List.of(Map.of("text", text))));
        try {
            RawResponse raw = post(url, payload, authHeaders, provider);
            if (!raw.status().is2xxSuccessful()) {
                return EmbeddingResult.error("Embedding call returned HTTP " + raw.status().value() + ": " + truncate(raw.body()),
                        raw.status().value());
            }
            JsonNode root = objectMapper.readTree(raw.body());
            JsonNode embedding = root.get("embedding");
            JsonNode values = embedding != null ? embedding.get("values") : null;
            if (values == null || !values.isArray()) {
                return EmbeddingResult.error("Embedding response had no embedding.values: " + truncate(raw.body()));
            }
            return EmbeddingResult.of(toFloatArray(values));
        } catch (Exception e) {
            return EmbeddingResult.error("Embedding call failed: " + truncate(e.getMessage()));
        }
    }

    private RawResponse post(String url, Map<String, Object> payload, Map<String, String> authHeaders, Provider provider) {
        boolean trustedOllama = provider.getType() == LlmProvider.OLLAMA;
        OutboundUrlGuard.Decision decision = urlGuard.check(url, properties.allowPrivateTargets(), trustedOllama);
        if (!decision.allowed()) {
            throw new IllegalStateException("Blocked by network policy: " + decision.reason());
        }
        return restClient.post()
                .uri(URI.create(url))
                .headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
                    authHeaders.forEach(headers::set);
                    provider.getDefaultHeaders().forEach((k, v) -> headers.set(k, String.valueOf(v)));
                })
                .body(payload)
                .exchange((req, res) -> new RawResponse(res.getStatusCode(), res.bodyTo(String.class)));
    }

    private float[] toFloatArray(JsonNode array) {
        float[] result = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = (float) array.get(i).asDouble();
        }
        return result;
    }

    private String truncate(String value) {
        if (value == null) {
            return "(no message)";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private record RawResponse(HttpStatusCode status, String body) {
    }
}
