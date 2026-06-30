package com.broksforge.modules.advisor.domain;

/**
 * How confident an advisor is in a recommendation. Confidence is deliberately
 * coarse — these are heuristic findings over observed signals, not proofs — so it is
 * surfaced to the engineer alongside every recommendation rather than hidden.
 */
public enum Confidence {
    LOW,
    MEDIUM,
    HIGH;

    public static Confidence parseOrDefault(String value) {
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
