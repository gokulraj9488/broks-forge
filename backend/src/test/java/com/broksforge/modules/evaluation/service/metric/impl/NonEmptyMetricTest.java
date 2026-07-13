package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NonEmptyMetricTest {

    private final NonEmptyMetric metric = new NonEmptyMetric();

    private MetricSpec spec() {
        return new MetricSpec(EvaluationMetricType.NON_EMPTY, null, null, null, Map.of());
    }

    @Test
    void passesForNonBlankOutput() {
        MetricOutcome outcome = metric.evaluate(spec(), new MetricContext("in", "hello", null, null, null, null));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void failsForNullOutput() {
        MetricOutcome outcome = metric.evaluate(spec(), new MetricContext("in", null, null, null, null, null));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    void failsForBlankOutput() {
        MetricOutcome outcome = metric.evaluate(spec(), new MetricContext("in", "   ", null, null, null, null));
        assertThat(outcome.passed()).isFalse();
    }
}
