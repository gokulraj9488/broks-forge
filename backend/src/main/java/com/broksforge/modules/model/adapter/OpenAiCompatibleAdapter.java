package com.broksforge.modules.model.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Base adapter for every provider that speaks the OpenAI Chat Completions wire format
 * verbatim: {@code {"model", "messages": [{"role","content"}]}} request,
 * {@code choices[].message.content} / {@code usage.prompt_tokens} response, and
 * {@code {"error": {"message","type","code"}}} errors. OpenAI, Groq, OpenRouter and Azure
 * OpenAI all use this shape unchanged — only {@link #providerType()} and the recognised route
 * suffix differ per subclass.
 */
abstract class OpenAiCompatibleAdapter implements ProviderAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ERROR_DETAIL_CHARS = 700;

    /** The path suffix that identifies this provider's chat-completions route. */
    protected String routeSuffix() {
        return "/chat/completions";
    }

    @Override
    public boolean supportsEndpoint(String endpointUrl) {
        if (endpointUrl == null) {
            return false;
        }
        try {
            String path = java.net.URI.create(endpointUrl).getPath();
            return path != null && path.toLowerCase(Locale.ROOT).endsWith(routeSuffix());
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
        payload.putAll(parameters);
        if (!payload.containsKey("max_tokens") && !payload.containsKey("max_completion_tokens")) {
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
            JsonNode usage = root.has("usage") ? root.get("usage") : root;
            Integer promptTokens = intByKeys(usage, List.of("prompt_tokens", "promptTokens"));
            Integer completionTokens = intByKeys(usage, List.of("completion_tokens", "completionTokens"));
            Integer totalTokens = intByKeys(usage, List.of("total_tokens", "totalTokens"));
            if (totalTokens == null && promptTokens != null && completionTokens != null) {
                totalTokens = promptTokens + completionTokens;
            }
            java.math.BigDecimal cost = decimalByKeys(root, List.of("cost", "total_cost", "totalCost"));
            if (cost == null) {
                cost = decimalByKeys(usage, List.of("cost", "total_cost", "totalCost"));
            }
            return new ParsedInvocation(output, promptTokens, completionTokens, totalTokens, cost, null);
        } catch (Exception notJson) {
            return new ParsedInvocation(body, null, null, null, null, null);
        }
    }

    private String extractOutput(JsonNode root, String raw) {
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            JsonNode message = first.get("message");
            if (message != null && message.hasNonNull("content")) {
                return message.get("content").asText();
            }
            if (first.hasNonNull("text")) {
                return first.get("text").asText();
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
            if (error != null) {
                if (error.isTextual()) {
                    return truncate(error.asText());
                }
                if (error.isObject()) {
                    String message = error.hasNonNull("message") ? error.get("message").asText() : null;
                    String type = error.hasNonNull("type") ? error.get("type").asText() : null;
                    String code = error.hasNonNull("code") ? error.get("code").asText() : null;
                    StringBuilder sb = new StringBuilder();
                    if (message != null) {
                        sb.append(message);
                    }
                    if (type != null || code != null) {
                        sb.append(" (");
                        if (type != null) {
                            sb.append("type=").append(type);
                        }
                        if (code != null) {
                            sb.append(type != null ? ", " : "").append("code=").append(code);
                        }
                        sb.append(')');
                    }
                    return sb.isEmpty() ? null : truncate(sb.toString());
                }
            }
            if (root.hasNonNull("message")) {
                return truncate(root.get("message").asText());
            }
        } catch (Exception notJson) {
            return null;
        }
        return null;
    }

    private Integer intByKeys(JsonNode node, List<String> keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isNumber()) {
                return value.asInt();
            }
        }
        return null;
    }

    private java.math.BigDecimal decimalByKeys(JsonNode node, List<String> keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isNumber()) {
                return value.decimalValue();
            }
        }
        return null;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_DETAIL_CHARS ? value : value.substring(0, MAX_ERROR_DETAIL_CHARS);
    }
}
