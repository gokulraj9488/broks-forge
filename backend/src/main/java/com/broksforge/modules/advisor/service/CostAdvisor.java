package com.broksforge.modules.advisor.service;

import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.Recommendation;
import com.broksforge.modules.advisor.domain.RecommendationCategory;
import com.broksforge.modules.advisor.domain.Severity;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.service.SummaryMetrics;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Analyses cost, token usage, latency and success rate across recent completed jobs
 * and recommends concrete cost optimisations: spend lost to failing runs, token bloat,
 * and where spend concentrates. Pure: operates only on the supplied job summaries.
 */
@Component
public class CostAdvisor {

    /** Average tokens per run above which the prompt/context is likely heavier than needed. */
    private static final double TOKENS_PER_RUN_BLOAT = 4000;

    public List<Recommendation> analyze(List<EvaluationJobResponse> jobs) {
        List<Recommendation> recs = new ArrayList<>();

        double windowCost = 0;
        double wastedCost = 0;
        long totalTokens = 0;
        long totalRuns = 0;
        EvaluationJobResponse costliest = null;
        double costliestCost = -1;

        for (EvaluationJobResponse job : jobs) {
            if (job.status() != EvaluationStatus.COMPLETED) {
                continue;
            }
            Double cost = SummaryMetrics.value(job.summary(), "totalCost");
            Double passRate = SummaryMetrics.value(job.summary(), "passRate");
            Double tokens = SummaryMetrics.value(job.summary(), "totalTokens");

            if (cost != null) {
                windowCost += cost;
                if (cost > costliestCost) {
                    costliestCost = cost;
                    costliest = job;
                }
                if (passRate != null && passRate < 0.5) {
                    wastedCost += cost * (1 - passRate);
                }
            }
            if (tokens != null) {
                totalTokens += tokens.longValue();
            }
            totalRuns += job.completedItems();
        }

        if (windowCost <= 0 && totalTokens <= 0) {
            return recs; // no cost/token signal reported by the endpoints yet
        }

        payingForFailures(recs, wastedCost, windowCost);
        tokenBloat(recs, totalTokens, totalRuns);
        concentration(recs, costliest, costliestCost, windowCost);

        return recs;
    }

    private void payingForFailures(List<Recommendation> recs, double wastedCost, double windowCost) {
        if (wastedCost <= 0 || windowCost <= 0) {
            return;
        }
        double pct = wastedCost / windowCost * 100;
        if (pct < 10) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.COST, "Spend is going to failing runs")
                .why(("About %s (~%.0f%% of recent evaluation spend) was incurred by runs that failed their metrics. "
                        + "Every failing run still pays for tokens, so quality problems are also cost problems.")
                        .formatted(money(wastedCost), pct))
                .howToFix("Fix the dominant failure mode first (see the Root-Cause Analysis for these jobs), and gate "
                        + "deploys on a regression check so failing configurations never reach production volume.")
                .expectedImprovement("Recover most of the ~%.0f%% wasted spend as failures are eliminated.".formatted(pct))
                .confidence(Confidence.MEDIUM)
                .severity(pct >= 30 ? Severity.HIGH : Severity.MEDIUM)
                .evidence("Estimated wasted spend: " + money(wastedCost))
                .evidence("Window spend: " + money(windowCost))
                .knowledgeKey("COST_SPIKE")
                .build());
    }

    private void tokenBloat(List<Recommendation> recs, long totalTokens, long totalRuns) {
        if (totalRuns <= 0 || totalTokens <= 0) {
            return;
        }
        double perRun = (double) totalTokens / totalRuns;
        if (perRun < TOKENS_PER_RUN_BLOAT) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.COST, "High token usage per run")
                .why(("Runs average ~%,.0f tokens each. High token usage drives cost and latency linearly and often "
                        + "comes from oversized prompts or unbounded outputs.")
                        .formatted(perRun))
                .howToFix("Trim the prompt (see Prompt Advisor), cap output length, and add a TOKEN_COUNT metric to your "
                        + "evaluation profile so token regressions are caught automatically.")
                .expectedImprovement("Lower cost and latency proportional to the token reduction.")
                .confidence(Confidence.MEDIUM)
                .severity(Severity.MEDIUM)
                .evidence("Average tokens/run: %,.0f".formatted(perRun))
                .evidence("Total tokens / runs: %,d / %,d".formatted(totalTokens, totalRuns))
                .knowledgeKey("TOKEN_BLOAT")
                .build());
    }

    private void concentration(List<Recommendation> recs, EvaluationJobResponse costliest, double costliestCost,
                               double windowCost) {
        if (costliest == null || windowCost <= 0 || costliestCost <= 0) {
            return;
        }
        double share = costliestCost / windowCost * 100;
        if (share < 60 || windowCost == costliestCost) {
            return;
        }
        recs.add(Recommendation.builder(RecommendationCategory.COST, "Spend is concentrated in one job")
                .why(("Job \"%s\" accounts for ~%.0f%% of recent evaluation spend. Concentrated spend is where "
                        + "optimisation has the most leverage.")
                        .formatted(costliest.name(), share))
                .howToFix("Focus model and prompt optimisation on this workload first; a small per-call saving here "
                        + "outweighs changes elsewhere.")
                .expectedImprovement("Optimising the dominant workload yields the largest absolute cost reduction.")
                .confidence(Confidence.LOW)
                .severity(Severity.LOW)
                .evidence("\"%s\" spend: %s of %s".formatted(costliest.name(), money(costliestCost), money(windowCost)))
                .knowledgeKey("COST_SPIKE")
                .build());
    }

    private String money(double value) {
        return "$" + String.format(Locale.ROOT, "%.4f", value);
    }
}
