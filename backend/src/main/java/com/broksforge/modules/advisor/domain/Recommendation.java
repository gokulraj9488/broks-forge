package com.broksforge.modules.advisor.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A single, actionable engineering recommendation — the unit every advisor and the
 * root-cause engine produce (ADR 0011). The shape is deliberately fixed to the
 * questions an engineer asks: <em>why</em> is this raised, <em>what changed</em>
 * (when change-driven), <em>how to fix</em> it, the <em>expected improvement</em>,
 * the <em>confidence</em>, the <em>severity</em>, and the supporting <em>evidence</em>.
 *
 * <p>Recommendations are computed on read from existing platform data and are never
 * persisted, so they always reflect current state (consistent with how benchmarks and
 * regressions are derived). An optional {@code knowledgeKey} links the recommendation
 * to a node in the Engineering Knowledge Graph.</p>
 */
public record Recommendation(
        RecommendationCategory category,
        String title,
        String why,
        String whatChanged,
        String howToFix,
        String expectedImprovement,
        Confidence confidence,
        Severity severity,
        List<String> evidence,
        String knowledgeKey
) {
    public Recommendation {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static Builder builder(RecommendationCategory category, String title) {
        return new Builder(category, title);
    }

    /** Fluent builder; keeps advisor code readable given the fixed, wide shape. */
    public static final class Builder {
        private final RecommendationCategory category;
        private final String title;
        private String why;
        private String whatChanged;
        private String howToFix;
        private String expectedImprovement;
        private Confidence confidence = Confidence.MEDIUM;
        private Severity severity = Severity.MEDIUM;
        private final List<String> evidence = new ArrayList<>();
        private String knowledgeKey;

        private Builder(RecommendationCategory category, String title) {
            this.category = category;
            this.title = title;
        }

        public Builder why(String why) {
            this.why = why;
            return this;
        }

        public Builder whatChanged(String whatChanged) {
            this.whatChanged = whatChanged;
            return this;
        }

        public Builder howToFix(String howToFix) {
            this.howToFix = howToFix;
            return this;
        }

        public Builder expectedImprovement(String expectedImprovement) {
            this.expectedImprovement = expectedImprovement;
            return this;
        }

        public Builder confidence(Confidence confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder evidence(String item) {
            if (item != null && !item.isBlank()) {
                this.evidence.add(item);
            }
            return this;
        }

        public Builder knowledgeKey(String knowledgeKey) {
            this.knowledgeKey = knowledgeKey;
            return this;
        }

        public Recommendation build() {
            return new Recommendation(category, title, why, whatChanged, howToFix, expectedImprovement,
                    confidence, severity, evidence, knowledgeKey);
        }
    }
}
