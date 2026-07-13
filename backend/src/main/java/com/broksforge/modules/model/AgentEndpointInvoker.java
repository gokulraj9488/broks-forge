package com.broksforge.modules.model;

import com.broksforge.common.security.OutboundUrlGuard;
import com.broksforge.config.properties.ModelInvocationProperties;
import com.broksforge.config.properties.ProviderDefaultsProperties;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.agent.service.HealthProbePlanner;
import com.broksforge.modules.model.adapter.ParsedInvocation;
import com.broksforge.modules.model.adapter.ProviderAdapter;
import com.broksforge.modules.model.adapter.ProviderAdapterRegistry;
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
 * <p>Most agents are the caller's own wrapper service, so the default request body is a stable
 * JSON envelope ({@code {"input": ..., "model": ..., "parameters": ...}}). When the endpoint
 * route matches a known hosted-LLM-provider shape, request construction and response parsing
 * are instead delegated to a {@link ProviderAdapter} (Provider abstraction milestone) —
 * OpenAI/Groq/OpenRouter's {@code /chat/completions}, Anthropic's {@code /messages}, or
 * Ollama's native {@code /api/chat} — each of which has its own request shape, response shape
 * and error envelope. An endpoint matching no adapter route keeps the original generic envelope
 * unchanged, so every previously-working {@code CUSTOM_REST} wrapper agent is unaffected.</p>
 *
 * <p>SSRF protection ({@link OutboundUrlGuard}) is applied before every call, and
 * authentication headers are supplied already-resolved by the caller so this class
 * never touches credentials directly. On a non-2xx response the provider's own error body is
 * parsed for a human-readable message and surfaced in the result instead of being discarded, so
 * root-cause analysis shows the real rejection reason rather than a bare status code.</p>
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
    private static final int MAX_ERROR_DETAIL_CHARS = 700;

    private final OutboundUrlGuard urlGuard;
    private final ModelInvocationProperties properties;
    private final ProviderDefaultsProperties providerDefaults;
    private final ProviderAdapterRegistry adapterRegistry;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AgentEndpointInvoker(OutboundUrlGuard urlGuard,
                                ModelInvocationProperties properties,
                                ProviderDefaultsProperties providerDefaults,
                                ProviderAdapterRegistry adapterRegistry,
                                ObjectMapper objectMapper) {
        this.urlGuard = urlGuard;
        this.properties = properties;
        this.providerDefaults = providerDefaults;
        this.adapterRegistry = adapterRegistry;
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
        ProviderAdapter adapter = adapterRegistry.resolve(target.endpointUrl());
        // Google AI Studio embeds the model in the URL path rather than the request body; every
        // other adapter's resolveInvocationUrl is a no-op returning endpointUrl unchanged.
        String invocationUrl = adapter != null
                ? adapter.resolveInvocationUrl(target.endpointUrl(), request.model()) : target.endpointUrl();

        boolean trustedOllama = adapter != null && adapter.providerType() == LlmProvider.OLLAMA;
        OutboundUrlGuard.Decision decision =
                urlGuard.check(invocationUrl, properties.allowPrivateTargets(), trustedOllama);
        if (!decision.allowed()) {
            return ModelInvocationResult.failure(null, 0, "Blocked by network policy: " + decision.reason());
        }

        // Defense in depth: every adapter that requires "model" in its wire payload (OpenAI-
        // compatible, Anthropic, Ollama) only adds the field when one is resolved — silently
        // dropping it rather than sending a malformed request. Evaluation-job creation already
        // validates this up front, but this catches every other caller (benchmarks, ad hoc
        // invocations, stale jobs created before that validation existed) before the HTTP call
        // instead of surfacing the provider's own "model is required" 400 mid-run.
        if (HealthProbePlanner.requiresModelField(target.endpointUrl())
                && (request.model() == null || request.model().isBlank())) {
            return ModelInvocationResult.failure(null, 0,
                    "This endpoint requires a model, and none was resolved (no evaluation override, "
                            + "agent version model, or provider default model is set)");
        }

        Map<String, Object> payload = buildPayload(request, target.endpointUrl(), adapter);

        long startNanos = System.nanoTime();
        try {
            log.debug("Model invocation → {} | payload keys={}", invocationUrl, payload.keySet());
            RawResponse raw = restClient.post()
                    .uri(URI.create(invocationUrl))
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
                String providerMessage = adapter != null ? adapter.parseError(raw.body())
                        : extractProviderErrorMessage(raw.body());
                String detail = providerMessage != null ? providerMessage : truncate(raw.body(), MAX_ERROR_DETAIL_CHARS);
                log.warn("Model invocation to {} failed: HTTP {} | body={}", invocationUrl, status,
                        truncate(raw.body(), MAX_ERROR_DETAIL_CHARS));
                return ModelInvocationResult.failure(status, latencyMs,
                        "Agent endpoint returned HTTP %d: %s".formatted(status,
                                (detail == null || detail.isBlank()) ? "(empty response body)" : detail));
            }
            return parse(raw.body(), status, latencyMs, adapter);
        } catch (Exception e) {
            long latencyMs = elapsedMs(startNanos);
            log.debug("Model invocation to {} failed: {}", invocationUrl, e.getMessage());
            return ModelInvocationResult.failure(null, latencyMs, truncate(e.getMessage(), 500));
        }
    }

    /**
     * Builds the outbound JSON body. When a {@link ProviderAdapter} matches the endpoint's route,
     * it owns request construction entirely (see {@link ProviderAdapter#buildPayload}), with a
     * provider-specific default completion-length cap from {@link ProviderDefaultsProperties}
     * applied only if the caller didn't already set one. Everything else keeps the original
     * generic {@code {"input", "model", "parameters"}} envelope unchanged.
     */
    private Map<String, Object> buildPayload(ModelInvocationRequest request, String endpointUrl,
                                             ProviderAdapter adapter) {
        if (adapter != null) {
            String host = URI.create(endpointUrl).getHost();
            String providerKey = HealthProbePlanner.providerConfigKey(HealthProbePlanner.detectProvider(host));
            int defaultMaxTokens = providerDefaults.maxTokensFor(providerKey);
            return adapter.buildPayload(request.model(), request.input(), request.parameters(), defaultMaxTokens);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", request.input());
        if (request.model() != null) {
            payload.put("model", request.model());
        }
        if (!request.parameters().isEmpty()) {
            payload.put("parameters", request.parameters());
        }
        return payload;
    }

    /**
     * Extracts a human-readable message from a provider's error body, if it's JSON shaped like
     * the OpenAI/Groq/OpenRouter convention ({@code {"error": {"message": ..., "type": ...,
     * "code": ...}}}) or the simpler {@code {"message": ...}} some providers use. Used only for
     * the generic envelope path — an adapter-matched endpoint uses {@link ProviderAdapter#parseError}
     * instead. Returns {@code null} (falling back to a raw body snippet) for anything else.
     */
    private String extractProviderErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.get("error");
            if (error != null) {
                if (error.isTextual()) {
                    return truncate(error.asText(), MAX_ERROR_DETAIL_CHARS);
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
                    return sb.isEmpty() ? null : truncate(sb.toString(), MAX_ERROR_DETAIL_CHARS);
                }
            }
            if (root.hasNonNull("message")) {
                return truncate(root.get("message").asText(), MAX_ERROR_DETAIL_CHARS);
            }
        } catch (Exception notJson) {
            return null;
        }
        return null;
    }

    private ModelInvocationResult parse(String body, int status, long latencyMs, ProviderAdapter adapter) {
        if (adapter != null) {
            ParsedInvocation parsed = adapter.parseSuccess(body);
            if (parsed.blockedReason() != null) {
                // A 2xx response that the provider itself refused to fulfil (e.g. Google AI
                // Studio's safety filter) is not a usable result — record it as a failure with
                // the real reason rather than a "successful" blank output that would be
                // misdiagnosed as a quality failure instead of a policy block.
                return ModelInvocationResult.failure(status, latencyMs, parsed.blockedReason());
            }
            return new ModelInvocationResult(true, truncate(parsed.output(), properties.maxOutputChars()),
                    status, latencyMs, parsed.promptTokens(), parsed.completionTokens(), parsed.totalTokens(),
                    parsed.cost(), null);
        }
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
