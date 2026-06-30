package com.broksforge.modules.agent.service;

import com.broksforge.common.security.OutboundUrlGuard;
import com.broksforge.config.properties.AgentHealthProperties;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentHealthStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

/**
 * Performs the actual outbound health probe against an agent endpoint.
 *
 * <p>Pure infrastructure with a single responsibility: given an agent, make one
 * HTTP call and translate the outcome into a {@link HealthProbeResult}. It never
 * touches the database, so it can be reused verbatim by the future automated
 * scheduler. SSRF protection and credential resolution are delegated to the
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

    public HealthProbeResult probe(Agent agent) {
        OutboundUrlGuard.Decision decision = urlGuard.check(agent.getEndpointUrl(), properties.allowPrivateTargets());
        if (!decision.allowed()) {
            return new HealthProbeResult(AgentHealthStatus.UNHEALTHY, false, null, null, decision.reason());
        }

        Map<String, String> authHeaders = credentialService.resolveAuthHeaders(agent);
        long startNanos = System.nanoTime();
        try {
            HttpStatusCode statusCode = restClient.get()
                    .uri(URI.create(agent.getEndpointUrl()))
                    .headers(headers -> authHeaders.forEach(headers::set))
                    .exchange((request, response) -> response.getStatusCode());

            long latencyMs = elapsedMs(startNanos);
            return classify(statusCode, latencyMs);
        } catch (Exception e) {
            long latencyMs = elapsedMs(startNanos);
            log.debug("Health probe for agent {} failed: {}", agent.getId(), e.getMessage());
            return new HealthProbeResult(AgentHealthStatus.UNHEALTHY, false, null, latencyMs, truncate(e.getMessage()));
        }
    }

    private HealthProbeResult classify(HttpStatusCode statusCode, long latencyMs) {
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
            reason = "Endpoint returned client error " + code;
        } else {
            status = AgentHealthStatus.UNHEALTHY;
            success = false;
            reason = "Endpoint returned server error " + code;
        }
        return new HealthProbeResult(status, success, code, latencyMs, reason);
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
