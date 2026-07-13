package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegexMatchMetricTest {

    private final RegexMatchMetric metric = new RegexMatchMetric();

    private MetricContext ctx(String output) {
        return new MetricContext("input", output, null, null, null, null);
    }

    private MetricSpec spec(Map<String, Object> params) {
        return new MetricSpec(EvaluationMetricType.REGEX_MATCH, null, null, null, params);
    }

    @Test
    void passesOnPartialMatchByDefault() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of("pattern", "\\d+")), ctx("order number 12345"));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void failsWhenFullMatchRequestedButOnlyPartialMatches() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of("pattern", "\\d+", "fullMatch", true)), ctx("order number 12345"));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    void failsCleanlyOnInvalidPattern() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of("pattern", "[unclosed")), ctx("anything"));
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.detail()).contains("Invalid regex pattern");
    }

    @Test
    void failsWhenNoPatternConfigured() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of()), ctx("anything"));
        assertThat(outcome.passed()).isFalse();
    }
}
