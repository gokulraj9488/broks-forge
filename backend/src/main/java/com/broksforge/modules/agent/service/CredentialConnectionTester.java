package com.broksforge.modules.agent.service;

import com.broksforge.common.security.OutboundUrlGuard;
import com.broksforge.config.properties.AgentHealthProperties;
import com.broksforge.modules.agent.domain.AgentFramework;
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
 * Performs a one-shot connection test against an agent endpoint using a supplied set of auth headers
 * (resolved from a saved credential, or assembled from an unsaved draft).
 *
 * <p>The probe is <b>provider-aware</b>: it uses {@link HealthProbePlanner} to choose the right method
 * and URL for the target — so, for an OpenAI-compatible endpoint such as Groq's
 * {@code /openai/v1/chat/completions}, it validates via {@code GET /openai/v1/models} (token-free)
 * rather than a naive GET of the POST-only completions path (which returns 404). Unlike
 * {@link AgentHealthCheckExecutor} it persists nothing and classifies from a <em>credential</em>
 * point of view — a 401/403 is reported as an authentication failure.</p>
 *
 * <p>Every probe passes through {@link OutboundUrlGuard} first (SSRF defence). The outgoing request and
 * response are logged at DEBUG with the Authorization header masked.</p>
 */
@Slf4j
@Component
public class CredentialConnectionTester {

    private final OutboundUrlGuard urlGuard;
    private final AgentHealthProperties properties;
    private final RestClient restClient;

    public CredentialConnectionTester(OutboundUrlGuard urlGuard, AgentHealthProperties properties) {
        this.urlGuard = urlGuard;
        this.properties = properties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.toIntExact(properties.timeoutMs());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Result of a connection test. Contains no secret material. */
    public record Result(boolean success, Integer httpStatus, long latencyMs, String message) {
    }

    private record Outcome(HttpStatusCode status, String body) {
    }

    public Result test(String endpointUrl, AgentFramework framework, Map<String, String> authHeaders) {
        HealthProbePlanner.ProbePlan plan = HealthProbePlanner.plan(endpointUrl, framework, null);

        OutboundUrlGuard.Decision decision = urlGuard.check(plan.url(), properties.allowPrivateTargets());
        if (!decision.allowed()) {
            return new Result(false, null, 0L, "Blocked by network policy: " + decision.reason());
        }

        log.debug("Connection test → {} {} | strategy={} | headers={} | body={}",
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

            long latencyMs = elapsedMs(startNanos);
            log.debug("Connection test ← {} {} | status={} | responseBody={}",
                    plan.method(), plan.url(), outcome.status().value(), outcome.body());
            return classify(outcome.status(), latencyMs);
        } catch (Exception e) {
            log.debug("Connection test to {} failed: {}", plan.url(), e.getMessage());
            return new Result(false, null, elapsedMs(startNanos), truncate(e.getMessage()));
        }
    }

    private Result classify(HttpStatusCode statusCode, long latencyMs) {
        int code = statusCode.value();
        if (statusCode.is2xxSuccessful() || statusCode.is3xxRedirection()) {
            return new Result(true, code, latencyMs, "Connected successfully (HTTP " + code + ")");
        }
        if (code == 401 || code == 403) {
            return new Result(false, code, latencyMs,
                    "Authentication rejected (HTTP " + code + ") — check the secret and header settings");
        }
        if (statusCode.is4xxClientError()) {
            return new Result(false, code, latencyMs, "Reachable but returned HTTP " + code);
        }
        return new Result(false, code, latencyMs, "Endpoint error (HTTP " + code + ")");
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
