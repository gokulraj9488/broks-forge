package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LengthMetricTest {

    private final LengthMetric metric = new LengthMetric();

    private MetricContext ctx(String output) {
        return new MetricContext("in", output, null, null, null, null);
    }

    private MetricSpec spec(Map<String, Object> params) {
        return new MetricSpec(EvaluationMetricType.LENGTH, null, null, null, params);
    }

    @Test
    void passesWithinBounds() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of("min", 3, "max", 10)), ctx("hello"));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void failsBelowMin() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of("min", 10)), ctx("hi"));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    void failsAboveMax() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of("max", 3)), ctx("hello"));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    void passesWithNoBoundsConfigured() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of()), ctx("anything at all"));
        assertThat(outcome.passed()).isTrue();
    }
}
