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
 * Anthropic's Messages API ({@code /v1/messages}) — a materially different wire format from
 * the OpenAI-compatible providers: {@code max_tokens} is a required top-level field (not
 * optional), the response body is {@code content: [{"type","text"}]} (an array of content
 * blocks, not a single string), and token usage is {@code usage.input_tokens} /
 * {@code usage.output_tokens} rather than {@code prompt_tokens}/{@code completion_tokens}.
 *
 * <p>Before this adapter existed, {@code AgentEndpointInvoker} had no Anthropic-specific
 * parsing: its generic {@code choices[]}/{@code OUTPUT_KEYS} extraction does not recognise
 * Anthropic's {@code content} array shape (it only handles {@code content} as a plain string),
 * so an Anthropic response would have been mis-parsed as the raw JSON dumped to a string
 * rather than the actual reply text.</p>
 */
@Component
public class AnthropicAdapter implements ProviderAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ERROR_DETAIL_CHARS = 700;

    @Override
    public LlmProvider providerType() {
        return LlmProvider.ANTHROPIC;
    }

    @Override
    public boolean supportsEndpoint(String endpointUrl) {
        if (endpointUrl == null) {
            return false;
        }
        try {
            String path = URI.create(endpointUrl).getPath();
            return path != null && path.toLowerCase(Locale.ROOT).endsWith("/messages");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Anthropic expects the key in {@code x-api-key} plus a required {@code anthropic-version} header. */
    @Override
    public Map<String, String> buildAuthHeaders(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Map.of();
        }
        return Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01");
    }

    @Override
    public Map<String, Object> buildPayload(String model, String input, Map<String, Object> parameters,
                                            int defaultMaxTokens) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (model != null) {
            payload.put("model", model);
        }
        payload.put("messages", List.of(Map.of("role", "user", "content", input)));
        payload.putAll(parameters);
        if (!payload.containsKey("max_tokens")) {
            // Anthropic requires max_tokens on every request — there is no provider-side default.
            payload.put("max_tokens", defaultMaxTokens);
        }
        return payload;
    }

    @Override
    public ParsedInvocation parseSuccess(String body) {
        if (body == null || body.isBlank()) {
            return new ParsedInvocation("", null, null, null, null, null);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            String output = extractOutput(root, body);
            JsonNode usage = root.get("usage");
            Integer promptTokens = intField(usage, "input_tokens");
            Integer completionTokens = intField(usage, "output_tokens");
            Integer totalTokens = (promptTokens != null && completionTokens != null)
                    ? promptTokens + completionTokens : null;
            return new ParsedInvocation(output, promptTokens, completionTokens, totalTokens, null, null);
        } catch (Exception notJson) {
            return new ParsedInvocation(body, null, null, null, null, null);
        }
    }

    private String extractOutput(JsonNode root, String raw) {
        JsonNode content = root.get("content");
        if (content != null && content.isArray() && !content.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if (block.hasNonNull("text")) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(block.get("text").asText());
                }
            }
            if (!sb.isEmpty()) {
                return sb.toString();
            }
        }
        return raw;
    }

    @Override
    public String parseError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode error = root.get("error");
            if (error != null && error.isObject()) {
                String message = error.hasNonNull("message") ? error.get("message").asText() : null;
                String type = error.hasNonNull("type") ? error.get("type").asText() : null;
                if (message == null) {
                    return null;
                }
                String result = type != null ? message + " (type=" + type + ")" : message;
                return truncate(result);
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
