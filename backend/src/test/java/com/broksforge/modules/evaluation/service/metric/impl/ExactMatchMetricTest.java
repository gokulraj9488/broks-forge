package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExactMatchMetricTest {

    private final ExactMatchMetric metric = new ExactMatchMetric();

    private MetricContext ctx(String output, String expected) {
        return new MetricContext("input", output, expected, null, null, null);
    }

    private MetricSpec spec(Map<String, Object> params) {
        return new MetricSpec(EvaluationMetricType.EXACT_MATCH, null, null, null, params);
    }

    @Test
    void passesWhenOutputMatchesExpectedCaseInsensitive() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of()), ctx("Hello", "hello"));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void failsCaseInsensitiveMismatchWhenCaseSensitiveRequested() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of("caseSensitive", true)), ctx("Hello", "hello"));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    void passesWithNoExpectedOutputConfigured() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of()), ctx("anything", null));
        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.detail()).contains("No expected output");
    }

    @Test
    void trimsWhitespaceBeforeComparing() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of()), ctx("  hello  ", "hello"));
        assertThat(outcome.passed()).isTrue();
    }
}
