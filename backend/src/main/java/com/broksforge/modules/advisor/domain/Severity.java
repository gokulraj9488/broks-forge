package com.broksforge.modules.advisor.domain;

/**
 * Severity of an engineering recommendation, in ascending order of urgency. Used to
 * rank and group findings across every advisor and the root-cause engine.
 */
public enum Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /**
     * Parses a severity from a (possibly null) string — e.g. a knowledge node's
     * {@code defaultSeverity}. Falls back to {@link #MEDIUM} when absent/unrecognised
     * so a finding always carries a usable severity.
     */
    public static Severity parseOrDefault(String value) {
        if (value == null) {
            return MEDIUM;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
