package com.broksforge.modules.advisor.service;

import com.broksforge.config.properties.AdvisorProperties;
import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.Recommendation;
import com.broksforge.modules.advisor.domain.RecommendationCategory;
import com.broksforge.modules.advisor.domain.Severity;
import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.domain.AgentHealthStatus;
import com.broksforge.modules.agent.web.dto.AgentResponse;
import com.broksforge.modules.evaluation.service.SummaryMetrics;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyses a registered agent's configuration and its recent evaluation behaviour and
 * recommends reliability and resilience improvements: stale/absent health checks, high
 * failure rates pointing at missing retries, latency spikes, weak authentication and
 * insecure transport. Pure: operates on the supplied agent and its recent jobs.
 */
@Component
public class AgentAdvisor {

    private final AdvisorProperties properties;

    public AgentAdvisor(AdvisorProperties properties) {
        this.properties = properties;
    }

    public List<Recommendation> analyze(AgentResponse agent, List<EvaluationJobResponse> agentJobs) {
        List<Recommendation> recs = new ArrayList<>();

        health(recs, agent);
        reliability(recs, agentJobs);
        latency(recs, agentJobs);
        auth(recs, agent);
        transport(recs, agent);

        return recs;
    }

    private void health(List<Recommendation> recs, AgentResponse agent) {
        AgentHealthStatus status = agent.healthStatus();
        boolean neverChecked = agent.lastHealthCheckAt() == null || status == null
                || status == AgentHealthStatus.UNKNOWN;
        boolean unhealthy = status == AgentHealthStatus.UNHEALTHY || status == AgentHealthStatus.DEGRADED;
        if (!neverChecked && !unhealthy) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.AGENT,
                        neverChecked ? "Agent has no recent health check" : "Agent health is degraded")
                .why(neverChecked
                        ? "The platform has no successful health probe for this agent, so its availability is unknown. "
                        + "Evaluations may fail simply because the endpoint is unreachable."
                        : "The latest health probe reported " + status + ". An unhealthy endpoint produces failing "
                        + "runs that look like quality problems but are really availability problems.")
                .howToFix("Configure and run a health check for the agent endpoint, and investigate connectivity/auth "
                        + "before trusting evaluation results.")
                .expectedImprovement("Distinguishes availability failures from quality failures; fewer false negatives.")
                .confidence(Confidence.HIGH)
                .severity(unhealthy ? Severity.HIGH : Severity.MEDIUM)
                .evidence("Health status: " + (status == null ? "UNKNOWN" : status.name()))
                .evidence("Last health check: " + (agent.lastHealthCheckAt() == null ? "never" : agent.lastHealthCheckAt()))
                .knowledgeKey("MISSING_HEALTHCHECK")
                .build());
    }

    private void reliability(List<Recommendation> recs, List<EvaluationJobResponse> jobs) {
        long failed = 0;
        long total = 0;
        for (EvaluationJobResponse job : jobs) {
            failed += job.failedItems();
            total += job.completedItems() + job.failedItems();
        }
        if (total < properties.minSamplesForComparison()) {
            return;
        }
        double failRate = (double) failed / total;
        if (failRate < 0.2) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.RELIABILITY, "High execution failure rate")
                .why(("~%.0f%% of recent evaluation items against this agent failed to execute (%d of %d). A high "
                        + "infrastructure failure rate usually means missing retries, no timeout handling, or an "
                        + "unstable endpoint — not a model-quality problem.")
                        .formatted(failRate * 100, failed, total))
                .howToFix("Add bounded retries with backoff and an explicit timeout around the agent call, and add a "
                        + "circuit breaker so a flapping endpoint fails fast instead of poisoning every run.")
                .expectedImprovement("Fewer transient failures; pass rate rises toward the model's true quality.")
                .confidence(Confidence.MEDIUM)
                .severity(failRate >= 0.4 ? Severity.HIGH : Severity.MEDIUM)
                .evidence("Failed items: %d of %d".formatted(failed, total))
                .knowledgeKey("MISSING_RETRY")
                .build());
    }

    private void latency(List<Recommendation> recs, List<EvaluationJobResponse> jobs) {
        double sum = 0;
        int n = 0;
        for (EvaluationJobResponse job : jobs) {
            Double latency = SummaryMetrics.value(job.summary(), "avgLatencyMs");
            if (latency != null) {
                sum += latency;
                n++;
            }
        }
        if (n == 0) {
            return;
        }
        double avg = sum / n;
        if (avg <= properties.latencySpikeMs()) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.LATENCY, "Agent latency is high")
                .why(("Average run latency is ~%,.0fms, above the %,dms guideline. High latency hurts user experience "
                        + "and caps throughput on synchronous paths.")
                        .formatted(avg, properties.latencySpikeMs()))
                .howToFix("Profile the slowest stage (use the AI Debugger timeline), enable streaming if the client can "
                        + "consume it, cache deterministic sub-results, and consider a lower-latency model.")
                .expectedImprovement("Lower end-to-end latency and higher throughput per worker.")
                .confidence(Confidence.MEDIUM)
                .severity(Severity.MEDIUM)
                .evidence("Average latency: %,.0fms".formatted(avg))
                .knowledgeKey("HIGH_LATENCY")
                .build());
    }

    private void auth(List<Recommendation> recs, AgentResponse agent) {
        if (agent.authType() != AgentAuthType.NONE) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.AGENT, "Agent endpoint has no authentication")
                .why("The agent endpoint is configured with no authentication. An unauthenticated endpoint can be "
                        + "called by anyone who learns the URL, risking abuse and uncontrolled cost.")
                .howToFix("Require an API key or bearer token on the endpoint and store the secret on the agent "
                        + "(it is encrypted at rest and only used for outbound calls).")
                .expectedImprovement("Removes an unauthenticated attack surface and uncontrolled-cost exposure.")
                .confidence(Confidence.HIGH)
                .severity(Severity.MEDIUM)
                .evidence("Auth type: NONE")
                .knowledgeKey("MISSING_AUTH")
                .build());
    }

    private void transport(List<Recommendation> recs, AgentResponse agent) {
        String url = agent.endpointUrl() == null ? "" : agent.endpointUrl().toLowerCase(java.util.Locale.ROOT);
        boolean insecure = url.startsWith("http://")
                && !url.contains("localhost") && !url.contains("127.0.0.1");
        if (!insecure) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.AGENT, "Agent endpoint uses plaintext HTTP")
                .why("The endpoint is reached over http://, so requests — including any auth header and the prompt — "
                        + "travel unencrypted and can be intercepted or tampered with in transit.")
                .howToFix("Serve the agent over HTTPS and update the registered endpoint URL.")
                .expectedImprovement("Protects credentials and payloads in transit.")
                .confidence(Confidence.HIGH)
                .severity(Severity.HIGH)
                .evidence("Endpoint scheme: http://")
                .knowledgeKey("INSECURE_TRANSPORT")
                .build());
    }
}
