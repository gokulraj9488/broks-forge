package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomMetric — CUSTOM-type dispatch to named CustomMetricEvaluator beans")
class CustomMetricTest {

    private final CustomMetric metric = new CustomMetric(List.of(new NumericRangeCustomMetric()));

    private MetricContext ctx(String output) {
        return new MetricContext("in", output, null, null, null, null);
    }

    @Test
    @DisplayName("dispatches to the evaluator matching params.key")
    void dispatchesToMatchingEvaluator() {
        MetricSpec spec = new MetricSpec(EvaluationMetricType.CUSTOM, null, null, null,
                Map.of("key", "numeric-range", "min", 1, "max", 10));
        MetricOutcome outcome = metric.evaluate(spec, ctx("Satisfaction score: 8/10"));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    @DisplayName("fails clearly when no key is configured")
    void failsWithNoKey() {
        MetricSpec spec = new MetricSpec(EvaluationMetricType.CUSTOM, null, null, null, Map.of());
        MetricOutcome outcome = metric.evaluate(spec, ctx("anything"));
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.detail()).contains("No custom metric key configured");
    }

    @Test
    @DisplayName("fails clearly when the key has no registered evaluator")
    void failsWithUnknownKey() {
        MetricSpec spec = new MetricSpec(EvaluationMetricType.CUSTOM, null, null, null, Map.of("key", "does-not-exist"));
        MetricOutcome outcome = metric.evaluate(spec, ctx("anything"));
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.detail()).contains("No custom metric evaluator registered");
    }

    @Test
    @DisplayName("numeric-range evaluator fails when the extracted value is out of bounds")
    void numericRangeFailsOutOfBounds() {
        MetricSpec spec = new MetricSpec(EvaluationMetricType.CUSTOM, null, null, null,
                Map.of("key", "numeric-range", "min", 5, "max", 10));
        MetricOutcome outcome = metric.evaluate(spec, ctx("Satisfaction score: 2/10"));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    @DisplayName("numeric-range evaluator fails clearly when no numeric value is present")
    void numericRangeFailsWithNoNumber() {
        MetricSpec spec = new MetricSpec(EvaluationMetricType.CUSTOM, null, null, null,
                Map.of("key", "numeric-range", "min", 1, "max", 10));
        MetricOutcome outcome = metric.evaluate(spec, ctx("no number here"));
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.detail()).contains("No numeric value found");
    }
}
