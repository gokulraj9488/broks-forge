package com.broksforge.modules.evaluation.service.metric;

import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.domain.MetricSpec;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Shared scoring helpers for {@link EvaluationMetric} implementations — outcome/label
 * construction, detail truncation, and untyped {@code params} readers. Every built-in
 * metric extends this; a metric is free not to if it needs none of these helpers.
 */
public abstract class AbstractEvaluationMetric implements EvaluationMetric {

    protected static final BigDecimal PASS = BigDecimal.ONE;
    protected static final BigDecimal FAIL = BigDecimal.ZERO;
    private static final int MAX_DETAIL = 500;

    protected final MetricOutcome pass(MetricSpec spec, String detail) {
        return outcome(spec, true, null, detail);
    }

    protected final MetricOutcome outcome(MetricSpec spec, boolean passed, BigDecimal threshold, String detail) {
        return new MetricOutcome(
                spec.type(),
                spec.label() != null ? spec.label() : spec.type().name(),
                passed,
                passed ? PASS : FAIL,
                threshold,
                truncate(detail));
    }

    /** For metrics with a continuous score (judge, similarity) rather than a binary pass/fail. */
    protected final MetricOutcome scoredOutcome(MetricSpec spec, boolean passed, BigDecimal score,
                                                BigDecimal threshold, String detail) {
        return new MetricOutcome(
                spec.type(),
                spec.label() != null ? spec.label() : spec.type().name(),
                passed,
                score,
                threshold,
                truncate(detail));
    }

    /**
     * A metric that never ran — the provider call failed before producing a score. {@code passed}
     * and {@code score} are {@code null}; the reason lives in {@code executionStatus} instead, so
     * the Runs UI and root-cause engine never mistake a transport/auth failure for a real low score.
     */
    protected final MetricOutcome executionError(MetricSpec spec, MetricExecutionStatus status, String detail) {
        return new MetricOutcome(
                spec.type(),
                spec.label() != null ? spec.label() : spec.type().name(),
                null,
                null,
                spec.threshold(),
                truncate(detail),
                status);
    }

    protected final String strParam(MetricSpec spec, String key) {
        Object value = spec.paramsOrEmpty().get(key);
        return value == null ? null : String.valueOf(value);
    }

    protected final Integer intParam(MetricSpec spec, String key) {
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

    protected final BigDecimal decimalParam(MetricSpec spec, String key) {
        Object value = spec.paramsOrEmpty().get(key);
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    protected final UUID uuidParam(MetricSpec spec, String key) {
        String value = strParam(spec, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    protected final boolean boolParam(MetricSpec spec, String key, boolean defaultValue) {
        Object value = spec.paramsOrEmpty().get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    protected final String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    protected final String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
    }
}
