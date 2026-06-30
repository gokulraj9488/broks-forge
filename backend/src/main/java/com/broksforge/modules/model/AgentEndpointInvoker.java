package com.broksforge.modules.model;

import com.broksforge.common.security.OutboundUrlGuard;
import com.broksforge.config.properties.ModelInvocationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The default {@link ModelInvoker}: calls a registered agent's own HTTP endpoint.
 * This is the real, key-free execution path — the agent wraps whatever provider it
 * was built on, so the platform stays provider-agnostic (see ADR 0006).
 *
 * <p>SSRF protection ({@link OutboundUrlGuard}) is applied before every call, and
 * authentication headers are supplied already-resolved by the caller so this class
 * never touches credentials directly. The request body is a stable JSON envelope
 * ({@code {"input": ..., "model": ..., "parameters": ...}}); the response is parsed
 * leniently, looking for common output and token-usage fields.</p>
 */
@Slf4j
@Component
public class AgentEndpointInvoker implements ModelInvoker {

    private static final List<String> OUTPUT_KEYS =
            List.of("output", "response", "content", "text", "completion", "answer", "result", "message");
    private static final List<String> PROMPT_TOKEN_KEYS =
            List.of("prompt_tokens", "promptTokens", "input_tokens", "inputTokens");
    private static final List<String> COMPLETION_TOKEN_KEYS =
            List.of("completion_tokens", "completionTokens", "output_tokens", "outputTokens");
    private static final List<String> TOTAL_TOKEN_KEYS = List.of("total_tokens", "totalTokens", "tokens");

    private final OutboundUrlGuard urlGuard;
    private final ModelInvocationProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AgentEndpointInvoker(OutboundUrlGuard urlGuard,
                                ModelInvocationProperties properties,
                                ObjectMapper objectMapper) {
        this.urlGuard = urlGuard;
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.toIntExact(properties.timeoutMs());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String name() {
        return "agent-endpoint";
    }

    @Override
    public boolean supports(ModelInvocationRequest request) {
        return request.target() != null && request.target().endpointUrl() != null;
    }

    @Override
    public ModelInvocationResult invoke(ModelInvocationRequest request) {
        ModelTarget target = request.target();
        OutboundUrlGuard.Decision decision = urlGuard.check(target.endpointUrl(), properties.allowPrivateTargets());
        if (!decision.allowed()) {
            return ModelInvocationResult.failure(null, 0, "Blocked by network policy: " + decision.reason());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", request.input());
        if (request.model() != null) {
            payload.put("model", request.model());
        }
        if (!request.parameters().isEmpty()) {
            payload.put("parameters", request.parameters());
        }

        long startNanos = System.nanoTime();
        try {
            RawResponse raw = restClient.post()
                    .uri(URI.create(target.endpointUrl()))
                    .headers(headers -> {
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
                        target.headers().forEach(headers::set);
                    })
                    .body(payload)
                    .exchange((req, res) -> new RawResponse(res.getStatusCode(), res.bodyTo(String.class)));

            long latencyMs = elapsedMs(startNanos);
            int status = raw.status().value();
            if (!raw.status().is2xxSuccessful()) {
                return ModelInvocationResult.failure(status, latencyMs,
                        "Agent endpoint returned HTTP " + status);
            }
            return parse(raw.body(), status, latencyMs);
        } catch (Exception e) {
            long latencyMs = elapsedMs(startNanos);
            log.debug("Model invocation to {} failed: {}", target.endpointUrl(), e.getMessage());
            return ModelInvocationResult.failure(null, latencyMs, truncate(e.getMessage(), 500));
        }
    }

    private ModelInvocationResult parse(String body, int status, long latencyMs) {
        if (body == null || body.isBlank()) {
            return new ModelInvocationResult(true, "", status, latencyMs, null, null, null, null, null);
        }
        String output;
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;
        BigDecimal cost = null;
        try {
            JsonNode root = objectMapper.readTree(body);
            output = extractOutput(root, body);
            JsonNode usage = root.has("usage") ? root.get("usage") : root;
            promptTokens = intByKeys(usage, PROMPT_TOKEN_KEYS);
            completionTokens = intByKeys(usage, COMPLETION_TOKEN_KEYS);
            totalTokens = intByKeys(usage, TOTAL_TOKEN_KEYS);
            if (totalTokens == null && promptTokens != null && completionTokens != null) {
                totalTokens = promptTokens + completionTokens;
            }
            cost = decimalByKeys(root, List.of("cost", "total_cost", "totalCost"));
            if (cost == null) {
                cost = decimalByKeys(usage, List.of("cost", "total_cost", "totalCost"));
            }
        } catch (Exception notJson) {
            output = body;
        }
        return new ModelInvocationResult(true, truncate(output, properties.maxOutputChars()),
                status, latencyMs, promptTokens, completionTokens, totalTokens, cost, null);
    }

    private String extractOutput(JsonNode root, String raw) {
        if (root.isTextual()) {
            return root.asText();
        }
        if (root.isObject()) {
            // OpenAI-style choices[].message.content / choices[].text
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
            for (String key : OUTPUT_KEYS) {
                JsonNode node = root.get(key);
                if (node != null && !node.isNull()) {
                    return node.isValueNode() ? node.asText() : node.toString();
                }
            }
        }
        return raw;
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

    private BigDecimal decimalByKeys(JsonNode node, List<String> keys) {
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

    private long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record RawResponse(HttpStatusCode status, String body) {
    }
}
