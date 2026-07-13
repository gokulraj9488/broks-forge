package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContainsMetricTest {

    private final ContainsMetric metric = new ContainsMetric();

    private MetricContext ctx(String output, String expected) {
        return new MetricContext("input", output, expected, null, null, null);
    }

    private MetricSpec spec(Map<String, Object> params) {
        return new MetricSpec(EvaluationMetricType.CONTAINS, null, null, null, params);
    }

    @Test
    void passesWhenOutputContainsConfiguredValueCaseInsensitive() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of("value", "WORLD")), ctx("hello world", null));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void fallsBackToExpectedOutputWhenNoValueParam() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of()), ctx("hello world", "world"));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void failsWhenSubstringAbsent() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of("value", "xyz")), ctx("hello world", null));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    void respectsCaseSensitiveFlag() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of("value", "WORLD", "caseSensitive", true)), ctx("hello world", null));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    void passesWhenNoSubstringToSearchFor() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of()), ctx("hello world", null));
        assertThat(outcome.passed()).isTrue();
    }
}
