package com.broksforge.modules.advisor.service;

import com.broksforge.config.properties.AdvisorProperties;
import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.Recommendation;
import com.broksforge.modules.advisor.domain.RecommendationCategory;
import com.broksforge.modules.advisor.domain.Severity;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.service.SummaryMetrics;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares the models used across recent completed evaluation jobs and recommends a
 * better trade-off — higher quality, lower cost or lower latency — against the team's
 * incumbent (most-used) model, with estimated savings. Pure: it operates only on the
 * job summaries handed to it.
 */
@Component
public class ModelAdvisor {

    private final AdvisorProperties properties;

    public ModelAdvisor(AdvisorProperties properties) {
        this.properties = properties;
    }

    public List<Recommendation> analyze(List<EvaluationJobResponse> jobs) {
        Map<String, Stats> byModel = new LinkedHashMap<>();
        int completed = 0;
        for (EvaluationJobResponse job : jobs) {
            if (job.status() != EvaluationStatus.COMPLETED || !StringUtils.hasText(job.model())) {
                continue;
            }
            completed++;
            String key = (job.provider() == null ? "" : job.provider().name() + " / ") + job.model();
            byModel.computeIfAbsent(key, Stats::new).add(job.summary());
        }

        if (byModel.size() < 2 || completed < properties.minSamplesForComparison()) {
            return List.of();
        }

        Stats incumbent = byModel.values().stream()
                .max(Comparator.comparingInt(s -> s.jobs))
                .orElseThrow();

        List<Recommendation> recs = new ArrayList<>();
        for (Stats candidate : byModel.values()) {
            if (candidate == incumbent) {
                continue;
            }
            recommendation(incumbent, candidate).ifPresent(recs::add);
        }
        return recs;
    }

    private java.util.Optional<Recommendation> recommendation(Stats incumbent, Stats candidate) {
        Double incQuality = incumbent.avg(incumbent.passRateSum, incumbent.passRateN);
        Double candQuality = candidate.avg(candidate.passRateSum, candidate.passRateN);
        Double incCost = incumbent.avg(incumbent.costSum, incumbent.costN);
        Double candCost = candidate.avg(candidate.costSum, candidate.costN);
        Double incLatency = incumbent.avg(incumbent.latencySum, incumbent.latencyN);
        Double candLatency = candidate.avg(candidate.latencySum, candidate.latencyN);

        // Higher quality at comparable-or-lower cost — the strongest recommendation.
        if (incQuality != null && candQuality != null && candQuality - incQuality >= 0.05
                && (incCost == null || candCost == null || candCost <= incCost * 1.05)) {
            return java.util.Optional.of(Recommendation.builder(RecommendationCategory.MODEL,
                            "Switch to a higher-quality model: " + candidate.key)
                    .why(("%s averages a %.0f%% pass rate vs %.0f%% for your most-used model %s, at comparable cost.")
                            .formatted(candidate.key, candQuality * 100, incQuality * 100, incumbent.key))
                    .whatChanged("Comparing models across your recent completed jobs.")
                    .howToFix("Register an agent version pinned to " + candidate.key
                            + " and re-run your evaluation suite to confirm the gain before promoting it.")
                    .expectedImprovement("~%.0f percentage-point higher pass rate.".formatted((candQuality - incQuality) * 100))
                    .confidence(confidenceFor(Math.min(incumbent.jobs, candidate.jobs)))
                    .severity(Severity.MEDIUM)
                    .evidence("%s: pass %.0f%%, avg cost %s".formatted(candidate.key, candQuality * 100, fmt(candCost)))
                    .evidence("%s: pass %.0f%%, avg cost %s".formatted(incumbent.key, incQuality * 100, fmt(incCost)))
                    .knowledgeKey("MODEL_OVERKILL")
                    .build());
        }

        // Comparable quality at materially lower cost — savings opportunity.
        if (incQuality != null && candQuality != null && Math.abs(candQuality - incQuality) < 0.03
                && incCost != null && candCost != null && incCost > 0 && candCost < incCost * 0.85) {
            double savingsPct = (incCost - candCost) / incCost * 100;
            return java.util.Optional.of(Recommendation.builder(RecommendationCategory.COST,
                            "Use a cheaper model at equivalent quality: " + candidate.key)
                    .why(("%s reaches a similar pass rate (%.0f%% vs %.0f%%) as %s but at ~%.0f%% lower cost per job. "
                            + "You are likely over-paying for quality you already get.")
                            .formatted(candidate.key, candQuality * 100, incQuality * 100, incumbent.key, savingsPct))
                    .whatChanged("Comparing models across your recent completed jobs.")
                    .howToFix("Shift traffic to " + candidate.key + " for this workload and keep "
                            + incumbent.key + " only where the extra capability is proven necessary.")
                    .expectedImprovement("~%.0f%% lower cost per job at equivalent quality.".formatted(savingsPct))
                    .confidence(confidenceFor(Math.min(incumbent.jobs, candidate.jobs)))
                    .severity(savingsPct >= 40 ? Severity.HIGH : Severity.MEDIUM)
                    .evidence("%s avg cost: %s".formatted(incumbent.key, fmt(incCost)))
                    .evidence("%s avg cost: %s".formatted(candidate.key, fmt(candCost)))
                    .knowledgeKey("SWITCH_CHEAPER_MODEL")
                    .build());
        }

        // Comparable quality at materially lower latency — speed opportunity.
        if (incQuality != null && candQuality != null && Math.abs(candQuality - incQuality) < 0.05
                && incLatency != null && candLatency != null && incLatency > 0 && candLatency < incLatency * 0.8) {
            double speedupPct = (incLatency - candLatency) / incLatency * 100;
            return java.util.Optional.of(Recommendation.builder(RecommendationCategory.LATENCY,
                            "Use a lower-latency model at equivalent quality: " + candidate.key)
                    .why(("%s is ~%.0f%% faster than %s (%.0fms vs %.0fms average) at a comparable pass rate.")
                            .formatted(candidate.key, speedupPct, incumbent.key, candLatency, incLatency))
                    .whatChanged("Comparing models across your recent completed jobs.")
                    .howToFix("Adopt " + candidate.key + " for latency-sensitive paths and validate quality with an "
                            + "evaluation run before rollout.")
                    .expectedImprovement("~%.0f%% lower average latency per call.".formatted(speedupPct))
                    .confidence(confidenceFor(Math.min(incumbent.jobs, candidate.jobs)))
                    .severity(Severity.LOW)
                    .evidence("%s avg latency: %.0fms".formatted(incumbent.key, incLatency))
                    .evidence("%s avg latency: %.0fms".formatted(candidate.key, candLatency))
                    .knowledgeKey("HIGH_LATENCY")
                    .build());
        }
        return java.util.Optional.empty();
    }

    private Confidence confidenceFor(int minJobs) {
        if (minJobs >= 5) {
            return Confidence.HIGH;
        }
        return minJobs >= 3 ? Confidence.MEDIUM : Confidence.LOW;
    }

    private String fmt(Double value) {
        return value == null ? "n/a" : String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    /** Per-model accumulator over job summaries. */
    private static final class Stats {
        private final String key;
        private int jobs;
        private double passRateSum;
        private int passRateN;
        private double costSum;
        private int costN;
        private double latencySum;
        private int latencyN;

        private Stats(String key) {
            this.key = key;
        }

        private void add(Map<String, Object> summary) {
            jobs++;
            Double passRate = SummaryMetrics.value(summary, "passRate");
            if (passRate != null) {
                passRateSum += passRate;
                passRateN++;
            }
            Double cost = SummaryMetrics.value(summary, "totalCost");
            if (cost != null) {
                costSum += cost;
                costN++;
            }
            Double latency = SummaryMetrics.value(summary, "avgLatencyMs");
            if (latency != null) {
                latencySum += latency;
                latencyN++;
            }
        }

        private Double avg(double sum, int n) {
            return n == 0 ? null : sum / n;
        }
    }
}
