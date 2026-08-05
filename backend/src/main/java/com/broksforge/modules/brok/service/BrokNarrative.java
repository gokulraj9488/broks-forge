package com.broksforge.modules.brok.service;

import java.util.List;
import java.util.Locale;

/**
 * Brok's voice.
 *
 * <p>Constitutionally Brok never says "I think". It states what is derived, what is inferred and what
 * is merely suggested, and it never invents a precision the evidence cannot support — which is why confidence
 * is a three-step verbal ladder here rather than a fabricated percentage. These helpers exist so every answer
 * and every brief speaks with the same register instead of each composer improvising its own.
 */
public final class BrokNarrative {

    // Epistemic statuses (L-33) — exactly one per statement.
    public static final String DERIVED = "derived";
    public static final String INFERRED = "inferred";
    public static final String SUGGESTED = "suggested";
    public static final String UNKNOWN_STATUS = "unknown";

    // Verdict states — the product's only evaluative vocabulary.
    public static final String HEALTHY = "healthy";
    public static final String ATTENTION = "attention";
    public static final String RISK = "risk";
    public static final String FAILED = "failed";
    public static final String UNKNOWN_STATE = "unknown";

    // Confidence ladder (L-57).
    public static final String CONSISTENT_WITH = "consistent-with";
    public static final String LIKELY = "likely";
    public static final String NEAR_CERTAIN = "near-certain";

    private BrokNarrative() {
    }

    /** Confidence derived from how much real evidence stands behind a statement — deliberately coarse. */
    public static String confidenceFor(int evidenceCount) {
        if (evidenceCount >= 8) {
            return NEAR_CERTAIN;
        }
        if (evidenceCount >= 3) {
            return LIKELY;
        }
        return CONSISTENT_WITH;
    }

    public static String plural(long count, String one) {
        return plural(count, one, one + "s");
    }

    public static String plural(long count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    /** "COMPLETED" → "Completed"; used wherever a stored enum has to be spoken aloud. */
    public static String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String cleaned = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    public static String percent(double ratio) {
        return Math.round(ratio * 100) + "%";
    }

    /** Joins names the way a person would: "a, b and c". */
    public static String list(List<String> values) {
        if (values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        return String.join(", ", values.subList(0, values.size() - 1)) + " and " + values.get(values.size() - 1);
    }

    /** "19 days ago", "yesterday", "3 hours ago" — read from the record's own clock, never a guess. */
    public static String agoWord(java.time.Instant then, java.time.Instant now) {
        if (then == null || now == null) {
            return "at an unrecorded time";
        }
        java.time.Duration d = java.time.Duration.between(then, now);
        long days = d.toDays();
        if (days >= 2) {
            return days + " days ago";
        }
        if (days == 1) {
            return "yesterday";
        }
        long hours = d.toHours();
        if (hours >= 2) {
            return hours + " hours ago";
        }
        long minutes = d.toMinutes();
        if (minutes >= 2) {
            return minutes + " minutes ago";
        }
        return "moments ago";
    }

    /** The worse of two verdict states, so a summary never reads better than its worst component. */
    public static String worseOf(String a, String b) {
        return severity(a) >= severity(b) ? a : b;
    }

    private static int severity(String state) {
        return switch (state) {
            case FAILED -> 4;
            case RISK -> 3;
            case ATTENTION -> 2;
            case UNKNOWN_STATE -> 1;
            default -> 0;
        };
    }
}
