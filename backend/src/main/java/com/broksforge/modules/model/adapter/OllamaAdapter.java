package com.broksforge.modules.model.adapter;

import com.broksforge.modules.agent.domain.LlmProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ollama's native chat API ({@code /api/chat}) — distinct from the OpenAI-compatible route
 * Ollama also exposes (which the OpenAI-shaped adapters already handle when the endpoint path
 * ends in {@code /chat/completions}). The native route wraps the reply in a single
 * {@code message} object rather than a {@code choices} array, reports token counts as
 * {@code prompt_eval_count}/{@code eval_count} instead of {@code usage.*}, has no numeric
 * completion-length cap ({@code max_tokens} isn't a native Ollama parameter — the closest
 * equivalent, {@code options.num_predict}, is left to the caller's own parameters rather than
 * defaulted here), and returns errors as a bare {@code {"error": "message"}} string, not an
 * object.
 *
 * <p>Ollama's native {@code /api/generate} endpoint (a raw prompt string rather than a
 * messages array) is not yet adapted — see the migration report's remaining-work section.</p>
 */
@Component
public class OllamaAdapter implements ProviderAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ERROR_DETAIL_CHARS = 700;

    @Override
    public LlmProvider providerType() {
        return LlmProvider.OLLAMA;
    }

    @Override
    public boolean supportsEndpoint(String endpointUrl) {
        if (endpointUrl == null) {
            return false;
        }
        try {
            String path = URI.create(endpointUrl).getPath();
            return path != null && path.toLowerCase(Locale.ROOT).endsWith("/api/chat");
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public Map<String, Object> buildPayload(String model, String input, Map<String, Object> parameters,
                                            int defaultMaxTokens) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (model != null) {
            payload.put("model", model);
        }
        payload.put("messages", List.of(Map.of("role", "user", "content", input)));
        payload.put("stream", false);
        payload.putAll(parameters);
        return payload;
    }

    @Override
    public ParsedInvocation parseSuccess(String body) {
        if (body == null || body.isBlank()) {
            return new ParsedInvocation("", null, null, null, null, null);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode message = root.get("message");
            String output = (message != null && message.hasNonNull("content"))
                    ? message.get("content").asText() : body;
            Integer promptTokens = intField(root, "prompt_eval_count");
            Integer completionTokens = intField(root, "eval_count");
            Integer totalTokens = (promptTokens != null && completionTokens != null)
                    ? promptTokens + completionTokens : null;
            return new ParsedInvocation(output, promptTokens, completionTokens, totalTokens, null, null);
        } catch (Exception notJson) {
            return new ParsedInvocation(body, null, null, null, null, null);
        }
    }

    @Override
    public String parseError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (root.hasNonNull("error")) {
                JsonNode error = root.get("error");
                String message = error.isTextual() ? error.asText()
                        : error.isObject() && error.hasNonNull("message") ? error.get("message").asText() : null;
                return message == null ? null : truncate(message);
            }
        } catch (Exception notJson) {
            return null;
        }
        return null;
    }

    private Integer intField(JsonNode node, String key) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(key);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private String truncate(String value) {
        return value.length() <= MAX_ERROR_DETAIL_CHARS ? value : value.substring(0, MAX_ERROR_DETAIL_CHARS);
    }
}
