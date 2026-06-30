package com.broksforge.modules.rootcause.service;

import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.Severity;
import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.service.MetricFailureTally;
import com.broksforge.modules.evaluation.service.SummaryMetrics;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationRunResponse;
import com.broksforge.modules.regression.web.dto.RegressionDtos.RegressionCheckResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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
                                             List<EvaluationRunResponse> failedRuns) {
        List<RootCauseFinding> findings = new ArrayList<>();
        Set<String> emitted = new HashSet<>();

        jobLevelFailure(job, findings, emitted);
        classifyRuns(failedRuns, findings, emitted);
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

    private void classifyRuns(List<EvaluationRunResponse> failedRuns, List<RootCauseFinding> findings,
                              Set<String> emitted) {
        if (failedRuns.isEmpty()) {
            return;
        }
        int total = failedRuns.size();
        int empty = 0;
        int http = 0;
        int timeout = 0;
        for (EvaluationRunResponse run : failedRuns) {
            String error = run.error() == null ? "" : run.error().toLowerCase(Locale.ROOT);
            boolean isTimeout = error.contains("timeout") || error.contains("timed out");
            boolean isHttp = run.httpStatus() != null && run.httpStatus() >= 400;
            boolean isEmpty = !StringUtils.hasText(run.output());
            if (isTimeout) {
                timeout++;
            } else if (isHttp) {
                http++;
            } else if (isEmpty) {
                empty++;
            }
        }

        if (timeout > 0 && !emitted.contains("TIMEOUT")) {
            emitted.add("TIMEOUT");
            findings.add(new RootCauseFinding(
                    "Agent calls are timing out",
                    severityForShare(timeout, total), confidenceForSample(total),
                    List.of("%d of %d sampled failed runs timed out".formatted(timeout, total)),
                    "Add a bounded timeout and retries with backoff around the agent call; profile the slow stage "
                            + "in the AI Debugger and consider a faster model or streaming.",
                    "Eliminates transient timeout failures and recovers their pass rate.",
                    "TIMEOUT"));
        }
        if (http > 0 && !emitted.contains("HTTP_ERROR")) {
            emitted.add("HTTP_ERROR");
            findings.add(new RootCauseFinding(
                    "The agent endpoint returned errors",
                    severityForShare(http, total), confidenceForSample(total),
                    List.of("%d of %d sampled failed runs returned HTTP >= 400".formatted(http, total)),
                    "Inspect the endpoint's error responses and auth; add retries for 5xx and fix 4xx request shape. "
                            + "Confirm the agent's health check is green.",
                    "Removes infrastructure failures that masquerade as quality failures.",
                    "HTTP_ERROR"));
        }
        if (empty > 0 && !emitted.contains("EMPTY_OUTPUT")) {
            emitted.add("EMPTY_OUTPUT");
            findings.add(new RootCauseFinding(
                    "The agent produced empty output",
                    severityForShare(empty, total), confidenceForSample(total),
                    List.of("%d of %d sampled failed runs had blank output".formatted(empty, total)),
                    "Check the endpoint contract and output parsing; ensure the prompt actually elicits a response and "
                            + "that the response field is being read correctly.",
                    "Converts blank responses into scored outputs and lifts the pass rate.",
                    "EMPTY_OUTPUT"));
        }
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
