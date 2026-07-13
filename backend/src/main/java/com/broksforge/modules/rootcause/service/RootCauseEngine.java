package com.broksforge.modules.rootcause.service;

import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.Severity;
import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.service.MetricExecutionFailureTally;
import com.broksforge.modules.evaluation.service.MetricFailureTally;
import com.broksforge.modules.evaluation.service.SummaryMetrics;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationRunResponse;
import com.broksforge.modules.regression.web.dto.RegressionDtos.RegressionCheckResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The pure, deterministic core of root-cause analysis (ADR 0012). Given a failed or
 * low-scoring job (its summary, per-metric pass/fail tallies and a sample of failed
 * runs) — or a regression check — it classifies the dominant failure modes into
 * {@link RootCauseFinding}s. No I/O: the {@code RootCauseService} loads the data and
 * enriches findings from the knowledge graph.
 */
@Component
public class RootCauseEngine {

    public List<RootCauseFinding> analyzeJob(EvaluationJobResponse job,
                                             List<MetricFailureTally> tallies,
                                             List<MetricExecutionFailureTally> executionFailures,
                                             List<EvaluationRunResponse> failedRuns) {
        List<RootCauseFinding> findings = new ArrayList<>();
        Set<String> emitted = new HashSet<>();

        jobLevelFailure(job, findings, emitted);
        classifyRuns(failedRuns, findings, emitted);
        classifyMetricExecutionFailures(executionFailures, findings, emitted);
        classifyMetrics(tallies, findings, emitted);

        if (findings.isEmpty()) {
            qualityFallback(job, findings);
        }
        findings.sort((a, b) -> b.severity().ordinal() - a.severity().ordinal());
        return findings;
    }

    public List<RootCauseFinding> analyzeRegression(RegressionCheckResponse check) {
        List<RootCauseFinding> findings = new ArrayList<>();
        if (!check.regressed()) {
            findings.add(new RootCauseFinding(
                    "No regression detected", Severity.INFO, Confidence.HIGH,
                    List.of("Candidate is within tolerance of the baseline on every measured dimension."),
                    "No action required.", "—", null));
            return findings;
        }
        for (Map.Entry<String, Object> entry : check.findings().entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> finding)) {
                continue;
            }
            if (!Boolean.TRUE.equals(finding.get("regressed"))) {
                continue;
            }
            findings.add(regressionFinding(entry.getKey(), finding));
        }
        findings.sort((a, b) -> b.severity().ordinal() - a.severity().ordinal());
        return findings;
    }

    // ----------------------------------------------------------------------
    // Job analysis
    // ----------------------------------------------------------------------

    private void jobLevelFailure(EvaluationJobResponse job, List<RootCauseFinding> findings, Set<String> emitted) {
        if (job.status() != EvaluationStatus.FAILED || !StringUtils.hasText(job.errorMessage())) {
            return;
        }
        emitted.add("HTTP_ERROR");
        findings.add(new RootCauseFinding(
                "The job aborted before completing",
                Severity.CRITICAL, Confidence.HIGH,
                List.of("Job status: FAILED", "Error: " + truncate(job.errorMessage(), 300)),
                "Verify the agent endpoint is reachable and correctly authenticated, then re-run the job. "
                        + "Check the AI Debugger timeline for the first failing run.",
                "Restores the job to a runnable state and unblocks evaluation.",
                "HTTP_ERROR"));
    }

    /**
     * Buckets failed runs by <em>why</em> they failed instead of lumping every non-2xx response
     * into one generic "the agent endpoint returned errors" finding. Distinguishing authentication,
     * quota/billing, rate-limiting, invalid-model, network and infrastructure failures matters
     * because each has a different fix — an operator chasing a prompt problem shouldn't be told
     * the same thing for a 401 (fix credentials) and a 500 (provider-side, wait and retry).
     * Classification here is purely from data already captured on the run (HTTP status, error
     * text) — no new instrumentation, no I/O.
     */
    private void classifyRuns(List<EvaluationRunResponse> failedRuns, List<RootCauseFinding> findings,
                              Set<String> emitted) {
        if (failedRuns.isEmpty()) {
            return;
        }
        int total = failedRuns.size();
        Map<FailureBucket, Integer> counts = new EnumMap<>(FailureBucket.class);
        for (EvaluationRunResponse run : failedRuns) {
            counts.merge(classify(run), 1, Integer::sum);
        }

        for (FailureBucket bucket : FailureBucket.values()) {
            int count = counts.getOrDefault(bucket, 0);
            if (count == 0 || emitted.contains(bucket.knowledgeKey)) {
                continue;
            }
            emitted.add(bucket.knowledgeKey);
            findings.add(new RootCauseFinding(
                    bucket.rootCause, severityForShare(count, total), confidenceForSample(total),
                    List.of(bucket.evidenceTemplate.formatted(count, total)),
                    bucket.recommendation, bucket.expectedImprovement, bucket.knowledgeKey));
        }
    }

    private FailureBucket classify(EvaluationRunResponse run) {
        String error = run.error() == null ? "" : run.error().toLowerCase(Locale.ROOT);
        Integer status = run.httpStatus();

        if (error.contains("timeout") || error.contains("timed out")) {
            return FailureBucket.TIMEOUT;
        }
        if (error.contains("blocked by network policy")) {
            return FailureBucket.NETWORK;
        }
        if (status == null) {
            boolean looksLikeNetwork = error.contains("connection") || error.contains("unknown host")
                    || error.contains("refused") || error.contains("unreachable") || error.contains("dns")
                    || error.contains("no route to host") || error.contains("ssl") || error.contains("handshake");
            if (looksLikeNetwork) {
                return FailureBucket.NETWORK;
            }
            return StringUtils.hasText(run.output()) ? FailureBucket.OTHER : FailureBucket.EMPTY_OUTPUT;
        }
        if (status == 401) {
            return FailureBucket.AUTHENTICATION;
        }
        boolean looksLikeQuota = error.contains("credit") || error.contains("quota") || error.contains("billing")
                || error.contains("insufficient") || error.contains("per day") || error.contains("tpd")
                || error.contains("daily limit");
        // Google's RESOURCE_EXHAUSTED status is its canonical rate-limit signal on HTTP 429 — its message
        // text says "quota" even for ordinary per-minute free-tier throttling, so it must win over the
        // generic quota-wording heuristic below, or every Google rate limit gets misfiled as billing/quota.
        boolean isGoogleRateLimit = status == 429 && error.contains("resource_exhausted");
        if (status == 403) {
            return looksLikeQuota ? FailureBucket.QUOTA : FailureBucket.AUTHENTICATION;
        }
        if (status == 402 || (status == 429 && !isGoogleRateLimit && looksLikeQuota)) {
            return FailureBucket.QUOTA;
        }
        if (status == 429) {
            return FailureBucket.RATE_LIMIT;
        }
        if (status == 404 && (error.contains("model") || error.contains("does not exist"))) {
            return FailureBucket.INVALID_MODEL;
        }
        if (status >= 500) {
            return FailureBucket.INFRASTRUCTURE;
        }
        if (status >= 400) {
            return FailureBucket.HTTP_ERROR;
        }
        return StringUtils.hasText(run.output()) ? FailureBucket.OTHER : FailureBucket.EMPTY_OUTPUT;
    }

    /**
     * Ordered so the most actionable/specific findings are classified first when a run could
     * plausibly match more than one bucket's textual heuristics.
     */
    private enum FailureBucket {
        TIMEOUT("Agent calls are timing out",
                "%d of %d sampled failed runs timed out",
                "Add a bounded timeout and retries with backoff around the agent call; profile the slow stage "
                        + "in the AI Debugger and consider a faster model or streaming.",
                "Eliminates transient timeout failures and recovers their pass rate.", "TIMEOUT"),
        NETWORK("Runs are failing to reach the agent endpoint over the network",
                "%d of %d sampled failed runs failed at the network layer (DNS, connection, TLS, or an "
                        + "outbound-network policy block)",
                "Verify the endpoint host/port is correct and reachable, check TLS certificates, and confirm the "
                        + "outbound network policy (SSRF guard) allows this target if it's an internal/private host.",
                "Restores connectivity so requests reach the provider at all.", "NETWORK_ERROR"),
        AUTHENTICATION("The agent's credentials are being rejected",
                "%d of %d sampled failed runs returned HTTP 401/403 (authentication/authorization failure)",
                "Verify the agent's API key or auth header is present, not expired, and has access to the "
                        + "requested model; re-check the credential in Agent settings.",
                "Removes authentication failures that block every request regardless of prompt or dataset quality.",
                "AUTHENTICATION_ERROR"),
        QUOTA("The account has run out of credits or quota",
                "%d of %d sampled failed runs were rejected for insufficient credits/quota (HTTP 402, or 429 "
                        + "reporting a credit/quota/billing limit)",
                "Top up the provider account's credit balance, request a higher quota, or reduce token usage "
                        + "(lower max_tokens, shorter prompts) until the account's limits are restored.",
                "Unblocks the run without needing any prompt, dataset, or code change.", "QUOTA_EXCEEDED"),
        RATE_LIMIT("Requests are being rate-limited by the provider",
                "%d of %d sampled failed runs returned HTTP 429 (rate limit)",
                "Reduce evaluation concurrency (worker-concurrency), add backoff between requests, or request a "
                        + "higher rate limit from the provider.",
                "Fewer transient rate-limit failures; more of the dataset completes per pass.", "RATE_LIMIT"),
        INVALID_MODEL("The configured model identifier is invalid or unavailable",
                "%d of %d sampled failed runs returned HTTP 404 indicating the model id doesn't exist for this "
                        + "provider",
                "Check the model field on the job or the agent's active version — it must be a real provider "
                        + "model id (e.g. llama-3.3-70b-versatile), not a provider name or a typo.",
                "Every request reaches a model the provider actually serves.", "INVALID_MODEL"),
        INFRASTRUCTURE("The provider is returning server-side errors",
                "%d of %d sampled failed runs returned HTTP >= 500 (provider-side infrastructure error)",
                "This is provider-side; add retries with backoff for 5xx (already applied in background runs) and "
                        + "check the provider's status page. No prompt/dataset change will fix this.",
                "Transient provider outages self-recover once retried.", "INFRASTRUCTURE_ERROR"),
        HTTP_ERROR("The agent endpoint rejected the request",
                "%d of %d sampled failed runs returned HTTP >= 400 with a malformed-request-shaped error",
                "Inspect the endpoint's error responses in the AI Debugger; the request shape (fields, headers) is "
                        + "likely wrong for this endpoint.",
                "Removes malformed-request failures that masquerade as quality failures.", "HTTP_ERROR"),
        EMPTY_OUTPUT("The agent produced empty output",
                "%d of %d sampled failed runs had blank output with no HTTP error",
                "Check the endpoint contract and output parsing; ensure the prompt actually elicits a response and "
                        + "that the response field is being read correctly.",
                "Converts blank responses into scored outputs and lifts the pass rate.", "EMPTY_OUTPUT"),
        OTHER("Runs are failing for an unclassified reason",
                "%d of %d sampled failed runs did not match a known failure pattern",
                "Inspect the run's error text and the AI Debugger timeline directly — this failure mode isn't "
                        + "recognised by the automated classifier yet.",
                "Surfaces the failure for manual triage instead of hiding it.", "UNCLASSIFIED_FAILURE");

        final String rootCause;
        final String evidenceTemplate;
        final String recommendation;
        final String expectedImprovement;
        final String knowledgeKey;

        FailureBucket(String rootCause, String evidenceTemplate, String recommendation,
                     String expectedImprovement, String knowledgeKey) {
            this.rootCause = rootCause;
            this.evidenceTemplate = evidenceTemplate;
            this.recommendation = recommendation;
            this.expectedImprovement = expectedImprovement;
            this.knowledgeKey = knowledgeKey;
        }
    }

    /**
     * Reports metrics that never actually ran — a judge/embedding call rejected for
     * authentication, an unreachable provider, a rate limit, an unknown model, or a timeout —
     * as the execution failure it is, instead of letting {@link #classifyMetrics} mistake it for
     * a real low score. These results are excluded from {@link MetricFailureTally} entirely (see
     * {@code EvaluationResultRepository#tallyByMetric}), so there's no double-reporting: a metric
     * tallies as either "ran and scored low" or "never ran", never both.
     */
    private void classifyMetricExecutionFailures(List<MetricExecutionFailureTally> tallies,
                                                 List<RootCauseFinding> findings, Set<String> emitted) {
        for (MetricExecutionFailureTally tally : tallies) {
            long count = tally.countOrZero();
            if (count == 0) {
                continue;
            }
            String family = isJudgeFamily(tally.metricType()) ? "Judge" : "Embedding";
            String knowledgeKey = family.toUpperCase(Locale.ROOT) + "_" + tally.status().name();
            if (emitted.contains(knowledgeKey)) {
                continue;
            }
            emitted.add(knowledgeKey);
            findings.add(executionFailureFinding(family, tally.metricType(), tally.status(), count, knowledgeKey));
        }
    }

    private boolean isJudgeFamily(EvaluationMetricType type) {
        return type == EvaluationMetricType.LLM_JUDGE || type == EvaluationMetricType.HALLUCINATION_DETECTION
                || type == EvaluationMetricType.CITATION_VERIFICATION;
    }

    private RootCauseFinding executionFailureFinding(String family, EvaluationMetricType metricType,
                                                     MetricExecutionStatus status, long count, String knowledgeKey) {
        String lowerFamily = family.toLowerCase(Locale.ROOT);
        String phrase = switch (status) {
            case AUTHENTICATION_ERROR -> "authentication failed";
            case PROVIDER_UNAVAILABLE -> "provider unavailable";
            case RATE_LIMITED -> "rate limited";
            case MODEL_NOT_FOUND -> "model unavailable";
            case TIMEOUT -> "timeout";
            case COMPLETED, INFRASTRUCTURE_ERROR -> "execution failed";
        };
        String recommendation = switch (status) {
            case AUTHENTICATION_ERROR -> "Check the " + lowerFamily + " provider's API key in Providers — it was "
                    + "rejected (HTTP 401/403).";
            case PROVIDER_UNAVAILABLE -> "Verify the " + lowerFamily + " provider is enabled and reachable, and "
                    + "that its endpoint shape is supported.";
            case RATE_LIMITED -> "Reduce evaluation concurrency or request a higher rate limit from the "
                    + lowerFamily + " provider.";
            case MODEL_NOT_FOUND -> "Check the " + (family.equals("Embedding") ? "embeddingModel" : "model")
                    + " param on this metric — it must be a real model id for the configured provider.";
            case TIMEOUT -> "The " + lowerFamily + " call is timing out — check the provider's latency or "
                    + "increase the invocation timeout.";
            case COMPLETED, INFRASTRUCTURE_ERROR -> "Inspect the metric's detail text on the flagged runs for the "
                    + "specific failure.";
        };
        int sample = count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
        return new RootCauseFinding(
                family + " " + phrase, Severity.HIGH, confidenceForSample(sample),
                List.of("%s (%s metric) failed to execute on %d run(s): %s".formatted(metricType.name(), family,
                        count, phrase)),
                recommendation,
                "The metric actually runs and produces a real score instead of always failing to execute.",
                knowledgeKey);
    }

    private void classifyMetrics(List<MetricFailureTally> tallies, List<RootCauseFinding> findings,
                                 Set<String> emitted) {
        List<MetricFailureTally> sorted = new ArrayList<>(tallies);
        sorted.sort((a, b) -> Long.compare(b.failedOrZero(), a.failedOrZero()));
        int added = 0;
        for (MetricFailureTally tally : sorted) {
            long failed = tally.failedOrZero();
            long total = failed + tally.passedOrZero();
            if (failed == 0 || total == 0) {
                continue;
            }
            double ratio = (double) failed / total;
            if (ratio < 0.3) {
                continue;
            }
            String key = knowledgeKeyFor(tally.metricType());
            if (emitted.contains(key)) {
                continue;
            }
            emitted.add(key);
            findings.add(metricFinding(tally, ratio, total, failed, key));
            if (++added >= 3) {
                break;
            }
        }
    }

    private RootCauseFinding metricFinding(MetricFailureTally tally, double ratio, long total, long failed,
                                           String key) {
        String metric = tally.metricType().name();
        Severity severity = ratio >= 0.6 ? Severity.HIGH : Severity.MEDIUM;
        List<String> evidence = List.of("%s failed on %d of %d runs (%.0f%%)".formatted(metric, failed, total, ratio * 100));
        return switch (tally.metricType()) {
            case JSON_VALID -> new RootCauseFinding(
                    "Output is not valid JSON", severity, Confidence.HIGH, evidence,
                    "Constrain the model to JSON (use a JSON/structured-output mode or a strict schema), and parse "
                            + "defensively. Add a JSON_VALID metric gate in CI.",
                    "Reliably parseable output and fewer downstream failures.", "JSON_PARSE_FAILURE");
            case EXACT_MATCH, CONTAINS, REGEX_MATCH -> new RootCauseFinding(
                    "Output does not match the expected answer", severity, Confidence.MEDIUM, evidence,
                    "Tighten the prompt (explicit task + format + one example), check the dataset's expected values, "
                            + "and consider a higher-quality model. Review the Prompt Advisor for this prompt.",
                    "Higher pass rate on the quality metrics.", "EXACT_MATCH_MISS");
            case NON_EMPTY -> new RootCauseFinding(
                    "Output is frequently empty", severity, Confidence.HIGH, evidence,
                    "Verify the endpoint contract and that the response field is read correctly; ensure the prompt "
                            + "elicits a response.",
                    "Eliminates blank outputs.", "EMPTY_OUTPUT");
            case SEMANTIC_SIMILARITY -> new RootCauseFinding(
                    "Output is semantically dissimilar from the expected answer", severity, Confidence.MEDIUM, evidence,
                    "Review the flagged runs' outputs against the expected answer — a low embedding similarity with "
                            + "an otherwise-reasonable-looking response often means the dataset's expected output "
                            + "needs updating, or the prompt needs tightening. Consider lowering the similarity "
                            + "threshold if wording is legitimately varying but semantically correct.",
                    "Higher pass rate on the semantic similarity metric.", "SEMANTIC_SIMILARITY_MISS");
            case LLM_JUDGE -> new RootCauseFinding(
                    "The LLM judge is scoring responses below threshold", severity, Confidence.MEDIUM, evidence,
                    "Read the judge's reasoning in the run detail for the flagged runs — it usually points directly "
                            + "at what's wrong (tone, missing steps, factual error). Tighten the prompt or rubric "
                            + "accordingly, or reconsider the judge threshold if it's too strict for this use case.",
                    "Higher pass rate on the judge metric.", "LLM_JUDGE_LOW_SCORE");
            case HALLUCINATION_DETECTION -> new RootCauseFinding(
                    "The model is fabricating unsupported claims", severity, Confidence.MEDIUM, evidence,
                    "Ground the prompt more tightly in the provided context/reference (or add one), instruct the "
                            + "model to say when it doesn't know, and consider a lower-temperature or "
                            + "retrieval-augmented setup.",
                    "Fewer unsupported claims in the output.", "HALLUCINATION_DETECTED");
            case CITATION_VERIFICATION -> new RootCauseFinding(
                    "Citations in the output don't match the provided context", severity, Confidence.MEDIUM, evidence,
                    "Check whether the agent has access to the real source context at generation time, and instruct "
                            + "it explicitly to only cite sources present in that context.",
                    "Citations become traceable and accurate.", "CITATION_MISMATCH");
            case CUSTOM -> new RootCauseFinding(
                    "A custom metric is failing", severity, Confidence.LOW, evidence,
                    "Inspect the custom metric's detail text on the flagged runs — the failure reason is specific to "
                            + "whatever that evaluator checks.",
                    "Higher pass rate on the custom metric.", "CUSTOM_METRIC_FAILURE");
            case LENGTH -> new RootCauseFinding(
                    "Output length is out of range", severity, Confidence.MEDIUM, evidence,
                    "Instruct the model on the expected length and add an output cap; adjust the LENGTH metric bounds "
                            + "if they are too strict.",
                    "Outputs land within the expected size envelope.", "EXACT_MATCH_MISS");
            case LATENCY -> new RootCauseFinding(
                    "Runs exceed the latency threshold", severity, Confidence.HIGH, evidence,
                    "Profile the slow stage (AI Debugger), enable streaming, cache deterministic steps, or adopt a "
                            + "lower-latency model.",
                    "Lower latency and more runs within threshold.", "HIGH_LATENCY");
            case COST -> new RootCauseFinding(
                    "Runs exceed the cost threshold", severity, Confidence.HIGH, evidence,
                    "Reduce tokens (trim the prompt, cap output) or switch to a cheaper model at equivalent quality "
                            + "(see the Model Advisor).",
                    "Lower per-run cost within budget.", "COST_SPIKE");
            case TOKEN_COUNT -> new RootCauseFinding(
                    "Token usage exceeds the threshold", severity, Confidence.HIGH, evidence,
                    "Trim prompt context and cap output length; the Prompt Advisor flags the biggest contributors.",
                    "Fewer tokens, lower cost and latency.", "TOKEN_BLOAT");
        };
    }

    private void qualityFallback(EvaluationJobResponse job, List<RootCauseFinding> findings) {
        Double passRate = SummaryMetrics.value(job.summary(), "passRate");
        if (passRate != null && passRate < 0.8) {
            findings.add(new RootCauseFinding(
                    "Quality is below target with no single dominant failure",
                    passRate < 0.5 ? Severity.HIGH : Severity.MEDIUM, Confidence.LOW,
                    List.of("Pass rate: %.0f%%".formatted(passRate * 100),
                            "No single failure mode dominates the sampled runs."),
                    "Inspect individual failing runs in the AI Debugger to find the pattern, and tighten the prompt "
                            + "or dataset expectations.",
                    "A clearer failure signal to target next.", "EXACT_MATCH_MISS"));
        } else {
            findings.add(new RootCauseFinding(
                    "No failure pattern detected", Severity.INFO, Confidence.MEDIUM,
                    List.of("No failed runs were sampled and pass rate is healthy."),
                    "No action required.", "—", null));
        }
    }

    // ----------------------------------------------------------------------
    // Regression analysis
    // ----------------------------------------------------------------------

    private RootCauseFinding regressionFinding(String key, Map<?, ?> finding) {
        String label = String.valueOf(finding.containsKey("label") ? finding.get("label") : key);
        Double deltaPct = asDouble(finding.get("deltaPct"));
        String delta = deltaPct == null ? "by an unclear margin" : "by %.1f%%".formatted(Math.abs(deltaPct));
        String knowledgeKey = switch (key) {
            case "avgLatencyMs" -> "HIGH_LATENCY";
            case "totalCost" -> "COST_SPIKE";
            case "totalTokens" -> "TOKEN_BLOAT";
            default -> "EXACT_MATCH_MISS";
        };
        Severity severity = deltaPct == null ? Severity.MEDIUM
                : Math.abs(deltaPct) >= 25 ? Severity.HIGH : Severity.MEDIUM;
        String recommendation = switch (key) {
            case "avgLatencyMs" -> "Find what slowed down between the two versions (model, prompt, or endpoint) and "
                    + "profile it in the AI Debugger.";
            case "totalCost" -> "Identify the cost driver (model change or token growth) and revert or optimise it; "
                    + "see the Cost Advisor.";
            case "totalTokens" -> "Trim the prompt/output growth introduced in the candidate version.";
            default -> "Compare the candidate's prompt and model against the baseline; the quality drop usually "
                    + "traces to a prompt or model change.";
        };
        return new RootCauseFinding(
                "%s regressed %s in the candidate".formatted(label, delta),
                severity, Confidence.MEDIUM,
                List.of("Baseline: " + finding.get("baseline"), "Candidate: " + finding.get("candidate"),
                        "Delta: " + (deltaPct == null ? "n/a" : "%.1f%%".formatted(deltaPct))),
                recommendation,
                "Returns the regressed dimension toward its baseline.",
                knowledgeKey);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private String knowledgeKeyFor(EvaluationMetricType type) {
        return switch (type) {
            case JSON_VALID -> "JSON_PARSE_FAILURE";
            case EXACT_MATCH, CONTAINS, REGEX_MATCH, LENGTH -> "EXACT_MATCH_MISS";
            case NON_EMPTY -> "EMPTY_OUTPUT";
            case SEMANTIC_SIMILARITY -> "SEMANTIC_SIMILARITY_MISS";
            case LLM_JUDGE -> "LLM_JUDGE_LOW_SCORE";
            case HALLUCINATION_DETECTION -> "HALLUCINATION_DETECTED";
            case CITATION_VERIFICATION -> "CITATION_MISMATCH";
            case CUSTOM -> "CUSTOM_METRIC_FAILURE";
            case LATENCY -> "HIGH_LATENCY";
            case COST -> "COST_SPIKE";
            case TOKEN_COUNT -> "TOKEN_BLOAT";
        };
    }

    private Severity severityForShare(int count, int total) {
        double share = total == 0 ? 0 : (double) count / total;
        return share >= 0.6 ? Severity.HIGH : Severity.MEDIUM;
    }

    private Confidence confidenceForSample(int total) {
        if (total >= 10) {
            return Confidence.HIGH;
        }
        return total >= 3 ? Confidence.MEDIUM : Confidence.LOW;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
