package com.broksforge.modules.evaluation.service;

import java.util.Map;
import java.util.Set;

/**
 * Published helper for reading and ranking the metric values stored in an evaluation
 * job's summary map. Centralises the knowledge of which metrics are
 * "lower-is-better" so benchmarking and regression rank consistently.
 */
public final class SummaryMetrics {

    /** Metrics where a smaller value is better (latency, cost, tokens, failures). */
    private static final Set<String> LOWER_IS_BETTER =
            Set.of("avgLatencyMs", "totalCost", "totalTokens", "failed");

    private SummaryMetrics() {
    }

    /**
     * Reads a numeric metric from a summary map. Returns {@code null} when the key is
     * absent or not numeric (the value may be stored as a number or a string).
     */
    public static Double value(Map<String, Object> summary, String key) {
        if (summary == null || key == null) {
            return null;
        }
        Object raw = summary.get(key);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static boolean higherIsBetter(String key) {
        return !LOWER_IS_BETTER.contains(key);
    }
}
