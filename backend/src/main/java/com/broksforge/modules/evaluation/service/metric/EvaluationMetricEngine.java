package com.broksforge.modules.evaluation.service.metric;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Scores a model output against a set of {@link MetricSpec}s. The engine is pure
 * (no persistence, no I/O) and deterministic, so it is trivially unit-testable and
 * reusable by benchmarking. Adding a metric is a code-only change: add an
 * {@link EvaluationMetricType} constant and a branch here.
 */
@Service
public class EvaluationMetricEngine {

    private static final BigDecimal PASS = BigDecimal.ONE;
    private static final BigDecimal FAIL = BigDecimal.ZERO;
    private static final int MAX_DETAIL = 500;

    private final ObjectMapper objectMapper;

    public EvaluationMetricEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates every spec against the context. When {@code specs} is empty a sensible
     * default rubric is used (non-empty output, plus exact-match when a reference exists).
     */
    public List<MetricOutcome> evaluate(List<MetricSpec> specs, MetricContext context) {
        List<MetricSpec> effective = (specs == null || specs.isEmpty()) ? defaultSpecs(context) : specs;
        List<MetricOutcome> outcomes = new ArrayList<>(effective.size());
        for (MetricSpec spec : effective) {
            outcomes.add(evaluateOne(spec, context));
        }
        return outcomes;
    }

    private List<MetricSpec> defaultSpecs(MetricContext context) {
        List<MetricSpec> defaults = new ArrayList<>();
        defaults.add(new MetricSpec(EvaluationMetricType.NON_EMPTY, null, null, null, Map.of()));
        if (context.expectedOutput() != null && !context.expectedOutput().isBlank()) {
            defaults.add(new MetricSpec(EvaluationMetricType.EXACT_MATCH, null, null, null, Map.of()));
        }
        return defaults;
    }

    private MetricOutcome evaluateOne(MetricSpec spec, MetricContext ctx) {
        return switch (spec.type()) {
            case EXACT_MATCH -> exactMatch(spec, ctx);
            case CONTAINS -> contains(spec, ctx);
            case REGEX_MATCH -> regexMatch(spec, ctx);
            case JSON_VALID -> jsonValid(spec, ctx);
            case NON_EMPTY -> nonEmpty(spec, ctx);
            case LENGTH -> length(spec, ctx);
            case LATENCY -> latency(spec, ctx);
            case COST -> cost(spec, ctx);
            case TOKEN_COUNT -> tokenCount(spec, ctx);
        };
    }

    // ---- Quality metrics -------------------------------------------------

    private MetricOutcome exactMatch(MetricSpec spec, MetricContext ctx) {
        if (ctx.expectedOutput() == null) {
            return pass(spec, "No expected output to compare against");
        }
        boolean caseSensitive = boolParam(spec, "caseSensitive", false);
        String a = nullToEmpty(ctx.output()).trim();
        String b = ctx.expectedOutput().trim();
        boolean ok = caseSensitive ? a.equals(b) : a.equalsIgnoreCase(b);
        return outcome(spec, ok, null, ok ? "Output matches expected" : "Output differs from expected");
    }

    private MetricOutcome contains(MetricSpec spec, MetricContext ctx) {
        String needle = strParam(spec, "value");
        if (needle == null) {
            needle = ctx.expectedOutput();
        }
        if (needle == null || needle.isEmpty()) {
            return pass(spec, "No substring to search for");
        }
        boolean caseSensitive = boolParam(spec, "caseSensitive", false);
        String haystack = nullToEmpty(ctx.output());
        boolean ok = caseSensitive
                ? haystack.contains(needle)
                : haystack.toLowerCase().contains(needle.toLowerCase());
        return outcome(spec, ok, null, ok ? "Output contains expected substring" : "Substring not found");
    }

    private MetricOutcome regexMatch(MetricSpec spec, MetricContext ctx) {
        String pattern = strParam(spec, "pattern");
        if (pattern == null || pattern.isEmpty()) {
            return outcome(spec, false, null, "No regex pattern configured");
        }
        try {
            boolean fullMatch = boolParam(spec, "fullMatch", false);
            Pattern compiled = Pattern.compile(pattern);
            var matcher = compiled.matcher(nullToEmpty(ctx.output()));
            boolean ok = fullMatch ? matcher.matches() : matcher.find();
            return outcome(spec, ok, null, ok ? "Output matches pattern" : "Output does not match pattern");
        } catch (PatternSyntaxException e) {
            return outcome(spec, false, null, "Invalid regex pattern");
        }
    }

    private MetricOutcome jsonValid(MetricSpec spec, MetricContext ctx) {
        String output = ctx.output();
        if (output == null || output.isBlank()) {
            return outcome(spec, false, null, "Output is empty");
        }
        try {
            objectMapper.readTree(output);
            return outcome(spec, true, null, "Output is valid JSON");
        } catch (Exception e) {
            return outcome(spec, false, null, "Output is not valid JSON");
        }
    }

    private MetricOutcome nonEmpty(MetricSpec spec, MetricContext ctx) {
        boolean ok = ctx.output() != null && !ctx.output().isBlank();
        return outcome(spec, ok, null, ok ? "Output is non-empty" : "Output is empty");
    }

    private MetricOutcome length(MetricSpec spec, MetricContext ctx) {
        int len = nullToEmpty(ctx.output()).length();
        Integer min = intParam(spec, "min");
        Integer max = intParam(spec, "max");
        boolean ok = (min == null || len >= min) && (max == null || len <= max);
        return outcome(spec, ok, null, "Output length " + len
                + " (min=" + (min == null ? "-" : min) + ", max=" + (max == null ? "-" : max) + ")");
    }

    // ---- Performance / cost metrics --------------------------------------

    private MetricOutcome latency(MetricSpec spec, MetricContext ctx) {
        BigDecimal threshold = spec.threshold();
        Long measured = ctx.latencyMs();
        if (threshold == null) {
            return outcome(spec, true, null, "Latency " + (measured == null ? "n/a" : measured + "ms"));
        }
        boolean ok = measured == null || BigDecimal.valueOf(measured).compareTo(threshold) <= 0;
        return outcome(spec, ok, threshold,
                "Latency " + (measured == null ? "n/a" : measured + "ms") + " vs <= " + threshold + "ms");
    }

    private MetricOutcome cost(MetricSpec spec, MetricContext ctx) {
        BigDecimal threshold = spec.threshold();
        BigDecimal measured = ctx.cost();
        if (threshold == null || measured == null) {
            return outcome(spec, true, threshold, "Cost " + (measured == null ? "not reported" : measured.toPlainString()));
        }
        boolean ok = measured.compareTo(threshold) <= 0;
        return outcome(spec, ok, threshold, "Cost " + measured.toPlainString() + " vs <= " + threshold.toPlainString());
    }

    private MetricOutcome tokenCount(MetricSpec spec, MetricContext ctx) {
        BigDecimal threshold = spec.threshold();
        Integer measured = ctx.totalTokens();
        if (threshold == null || measured == null) {
            return outcome(spec, true, threshold, "Tokens " + (measured == null ? "not reported" : measured));
        }
        boolean ok = BigDecimal.valueOf(measured).compareTo(threshold) <= 0;
        return outcome(spec, ok, threshold, "Tokens " + measured + " vs <= " + threshold);
    }

    // ---- Helpers ---------------------------------------------------------

    private MetricOutcome pass(MetricSpec spec, String detail) {
        return outcome(spec, true, null, detail);
    }

    private MetricOutcome outcome(MetricSpec spec, boolean passed, BigDecimal threshold, String detail) {
        return new MetricOutcome(
                spec.type(),
                spec.label() != null ? spec.label() : spec.type().name(),
                passed,
                passed ? PASS : FAIL,
                threshold,
                truncate(detail));
    }

    private String strParam(MetricSpec spec, String key) {
        Object value = spec.paramsOrEmpty().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer intParam(MetricSpec spec, String key) {
        Object value = spec.paramsOrEmpty().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean boolParam(MetricSpec spec, String key, boolean defaultValue) {
        Object value = spec.paramsOrEmpty().get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
    }
}
