package com.broksforge.modules.agent.service;

import com.broksforge.common.security.OutboundUrlGuard;
import com.broksforge.config.properties.AgentHealthProperties;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentHealthStatus;
import com.broksforge.modules.agent.domain.AgentVersion;
import com.broksforge.modules.agent.domain.LlmProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

/**
 * Performs the actual outbound health probe against an agent endpoint.
 *
 * <p>The probe is <b>provider-aware</b>: {@link HealthProbePlanner} chooses the
 * right method and URL for the target (Spring Boot → {@code /actuator/health},
 * FastAPI/LangGraph → {@code /health}, LLM-provider agents → a tiny POST
 * completion, others → a plain GET). Pure infrastructure with a single
 * responsibility: given an agent (and its active version), make one HTTP call and
 * translate the outcome into a {@link HealthProbeResult}. It never touches the
 * database, so it can be reused verbatim by the future automated scheduler. SSRF
 * protection and credential resolution are delegated to the
 * {@link OutboundUrlGuard} and {@link AgentCredentialService}.</p>
 */
@Slf4j
@Component
public class AgentHealthCheckExecutor {

    private final OutboundUrlGuard urlGuard;
    private final AgentCredentialService credentialService;
    private final AgentHealthProperties properties;
    private final RestClient restClient;

    public AgentHealthCheckExecutor(OutboundUrlGuard urlGuard,
                                    AgentCredentialService credentialService,
                                    AgentHealthProperties properties) {
        this.urlGuard = urlGuard;
        this.credentialService = credentialService;
        this.properties = properties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.toIntExact(properties.timeoutMs());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public HealthProbeResult probe(Agent agent, AgentVersion activeVersion) {
        LlmProvider provider = activeVersion != null ? activeVersion.getProvider() : null;
        HealthProbePlanner.ProbePlan plan =
                HealthProbePlanner.plan(agent.getEndpointUrl(), agent.getFramework(), provider);

        boolean trustedOllama =
                HealthProbePlanner.effectiveProvider(agent.getEndpointUrl(), provider) == LlmProvider.OLLAMA;
        OutboundUrlGuard.Decision decision =
                urlGuard.check(plan.url(), properties.allowPrivateTargets(), trustedOllama);
        if (!decision.allowed()) {
            return new HealthProbeResult(AgentHealthStatus.UNHEALTHY, false, null, null,
                    decision.reason(), plan.strategy(), plan.url());
        }

        Map<String, String> authHeaders = credentialService.resolveAuthHeaders(agent);
        log.debug("Health probe → {} {} | strategy={} | headers={} | body={}",
                plan.method(), plan.url(), plan.strategy(),
                ProbeSupport.maskedHeaders(authHeaders), plan.body());

        long startNanos = System.nanoTime();
        try {
            Outcome outcome = plan.method() == HttpMethod.POST
                    ? restClient.post()
                        .uri(URI.create(plan.url()))
                        .headers(headers -> {
                            headers.setContentType(MediaType.APPLICATION_JSON);
                            authHeaders.forEach(headers::set);
                        })
                        .body(plan.body())
                        .exchange((request, response) ->
                                new Outcome(response.getStatusCode(), ProbeSupport.bodySnippet(response, 500)))
                    : restClient.get()
                        .uri(URI.create(plan.url()))
                        .headers(headers -> authHeaders.forEach(headers::set))
                        .exchange((request, response) ->
                                new Outcome(response.getStatusCode(), ProbeSupport.bodySnippet(response, 500)));

            log.debug("Health probe ← {} {} | status={} | responseBody={}",
                    plan.method(), plan.url(), outcome.status().value(), outcome.body());
            return classify(outcome.status(), elapsedMs(startNanos), plan);
        } catch (Exception e) {
            long latencyMs = elapsedMs(startNanos);
            log.debug("Health probe for agent {} ({}) failed: {}", agent.getId(), plan.strategy(), e.getMessage());
            return new HealthProbeResult(AgentHealthStatus.UNHEALTHY, false, null, latencyMs,
                    truncate(e.getMessage()), plan.strategy(), plan.url());
        }
    }

    private record Outcome(HttpStatusCode status, String body) {
    }

    private HealthProbeResult classify(HttpStatusCode statusCode, long latencyMs, HealthProbePlanner.ProbePlan plan) {
        int code = statusCode.value();
        AgentHealthStatus status;
        boolean success;
        String reason = null;
        if (statusCode.is2xxSuccessful() || statusCode.is3xxRedirection()) {
            status = AgentHealthStatus.HEALTHY;
            success = true;
        } else if (statusCode.is4xxClientError()) {
            status = AgentHealthStatus.DEGRADED;
            success = false;
            reason = (code == 401 || code == 403)
                    ? "Authentication rejected (HTTP " + code + ")"
                    : "Endpoint returned client error " + code;
        } else {
            status = AgentHealthStatus.UNHEALTHY;
            success = false;
            reason = "Endpoint returned server error " + code;
        }
        return new HealthProbeResult(status, success, code, latencyMs, reason, plan.strategy(), plan.url());
    }

    private long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private String truncate(String message) {
        if (message == null) {
            return "Endpoint unreachable";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
