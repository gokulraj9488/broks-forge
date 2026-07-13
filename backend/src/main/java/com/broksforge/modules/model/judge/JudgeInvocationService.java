package com.broksforge.modules.model.judge;

import com.broksforge.common.security.CredentialEncryptionService;
import com.broksforge.common.security.OutboundUrlGuard;
import com.broksforge.config.properties.ModelInvocationProperties;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.model.adapter.ParsedInvocation;
import com.broksforge.modules.model.adapter.ProviderAdapter;
import com.broksforge.modules.model.adapter.ProviderAdapterRegistry;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls a stored {@link Provider} directly with a judge/rubric prompt — the missing piece
 * {@code ModelInvocationRequest}'s javadoc already anticipated ("a future provider-direct
 * invoker"). Used by the LLM Judge, Hallucination Detection and Citation Verification metrics,
 * which need to score a run by asking a model a question, not by calling the agent under test.
 *
 * <p>Deliberately independent of {@code AgentEndpointInvoker}: it resolves a {@link Provider} by
 * id (not an {@code Agent}/{@code ModelTarget}), reusing the same decrypt-then-adapt pattern
 * already used for provider-level auth fallback ({@code AgentCredentialService
 * .resolveProviderAuthHeaders}) and the same {@link OutboundUrlGuard} SSRF check
 * {@code AgentEndpointInvoker} applies to every outbound call.</p>
 */
@Slf4j
@Service
public class JudgeInvocationService {

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final int MAX_ERROR_CHARS = 500;

    private final ProviderRepository providerRepository;
    private final CredentialEncryptionService encryptionService;
    private final ProviderAdapterRegistry adapterRegistry;
    private final OutboundUrlGuard urlGuard;
    private final ModelInvocationProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public JudgeInvocationService(ProviderRepository providerRepository,
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

    /**
     * Sends {@code prompt} (the caller has already assembled rubric + context + output — adapters
     * only support a single flattened turn, the same constraint {@code AgentEndpointInvoker}
     * already lives with) to the given provider/model, expecting the judge to reply with strict
     * JSON: {@code {"score": 0.0-1.0, "reasoning": "..."}}. Never throws — every failure mode
     * (missing provider, disabled provider, network error, unparseable judge output) becomes a
     * {@link JudgeVerdict#error(String)} for the caller to turn into a failed metric outcome.
     */
    public JudgeVerdict judge(UUID providerId, String model, String prompt) {
        if (providerId == null) {
            return JudgeVerdict.error("No judge provider configured (params.providerId is required)");
        }
        Provider provider = providerRepository.findById(providerId).orElse(null);
        if (provider == null) {
            return JudgeVerdict.error("Judge provider " + providerId + " not found");
        }
        if (!provider.isEnabled()) {
            return JudgeVerdict.error("Judge provider '" + provider.getName() + "' is disabled");
        }
        String effectiveModel = (model != null && !model.isBlank()) ? model : provider.getDefaultModel();
        if (effectiveModel == null || effectiveModel.isBlank()) {
            return JudgeVerdict.error("No judge model configured (params.model or a provider default model)");
        }

        ProviderAdapter adapter = adapterRegistry.resolve(provider.getBaseUrl());
        String invocationUrl = adapter != null
                ? adapter.resolveInvocationUrl(provider.getBaseUrl(), effectiveModel) : provider.getBaseUrl();

        boolean trustedOllama = provider.getType() == LlmProvider.OLLAMA;
        OutboundUrlGuard.Decision decision =
                urlGuard.check(invocationUrl, properties.allowPrivateTargets(), trustedOllama);
        if (!decision.allowed()) {
            return JudgeVerdict.error("Blocked by network policy: " + decision.reason());
        }

        Map<String, Object> payload = adapter != null
                ? adapter.buildPayload(effectiveModel, prompt, Map.of(), 512)
                : Map.of("input", prompt, "model", effectiveModel);
        String apiKey = provider.getEncryptedApiKey() != null ? encryptionService.decrypt(provider.getEncryptedApiKey()) : null;
        Map<String, String> authHeaders = adapter != null
                ? adapter.buildAuthHeaders(apiKey)
                : (apiKey == null || apiKey.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + apiKey));

        try {
            RawResponse raw = restClient.post()
                    .uri(URI.create(invocationUrl))
                    .headers(headers -> {
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
                        authHeaders.forEach(headers::set);
                        provider.getDefaultHeaders().forEach((k, v) -> headers.set(k, String.valueOf(v)));
                    })
                    .body(payload)
                    .exchange((req, res) -> new RawResponse(res.getStatusCode(), res.bodyTo(String.class)));

            if (!raw.status().is2xxSuccessful()) {
                String message = adapter != null ? adapter.parseError(raw.body()) : null;
                return JudgeVerdict.error("Judge model returned HTTP " + raw.status().value()
                        + (message != null ? ": " + message : ""), raw.status().value());
            }
            String output;
            if (adapter != null) {
                ParsedInvocation parsed = adapter.parseSuccess(raw.body());
                if (parsed.blockedReason() != null) {
                    return JudgeVerdict.error("Judge call blocked: " + parsed.blockedReason());
                }
                output = parsed.output();
            } else {
                output = raw.body();
            }
            return parseVerdict(output);
        } catch (Exception e) {
            log.debug("Judge invocation to {} failed: {}", invocationUrl, e.getMessage());
            return JudgeVerdict.error("Judge call failed: " + truncate(e.getMessage()));
        }
    }

    /**
     * Parses the judge's reply, tolerating a markdown code fence around the JSON (models
     * routinely wrap strict-JSON instructions in ```json fences despite being told not to).
     */
    JudgeVerdict parseVerdict(String output) {
        if (output == null || output.isBlank()) {
            return JudgeVerdict.error("Judge model returned an empty response");
        }
        Matcher matcher = JSON_OBJECT.matcher(output);
        if (!matcher.find()) {
            return JudgeVerdict.error("Judge model did not return parseable JSON: " + truncate(output));
        }
        try {
            JsonNode node = objectMapper.readTree(matcher.group());
            if (!node.hasNonNull("score")) {
                return JudgeVerdict.error("Judge response is missing a numeric \"score\" field: " + truncate(output));
            }
            BigDecimal score = node.get("score").decimalValue();
            String reasoning = node.hasNonNull("reasoning") ? node.get("reasoning").asText() : null;
            Map<String, Object> criteria = Map.of();
            if (node.hasNonNull("criteria") && node.get("criteria").isObject()) {
                criteria = objectMapper.convertValue(node.get("criteria"), new TypeReference<Map<String, Object>>() { });
            }
            return JudgeVerdict.of(score, reasoning, criteria);
        } catch (Exception e) {
            return JudgeVerdict.error("Judge response is not valid JSON: " + truncate(output));
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "(no message)";
        }
        return value.length() <= MAX_ERROR_CHARS ? value : value.substring(0, MAX_ERROR_CHARS);
    }

    private record RawResponse(HttpStatusCode status, String body) {
    }
}
