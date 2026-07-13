package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the three threshold-comparison metrics together — same shape, different measured field. */
class LatencyCostTokenCountMetricTest {

    private final LatencyMetric latency = new LatencyMetric();
    private final CostMetric cost = new CostMetric();
    private final TokenCountMetric tokenCount = new TokenCountMetric();

    private MetricContext ctx(Long latencyMs, BigDecimal costValue, Integer tokens) {
        return new MetricContext("in", "out", null, latencyMs, tokens, costValue);
    }

    private MetricSpec spec(EvaluationMetricType type, BigDecimal threshold) {
        return new MetricSpec(type, null, null, threshold, Map.of());
    }

    @Test
    void latencyPassesWhenUnderThreshold() {
        MetricOutcome outcome = latency.evaluate(spec(EvaluationMetricType.LATENCY, BigDecimal.valueOf(1000)),
                ctx(500L, null, null));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void latencyFailsWhenOverThreshold() {
        MetricOutcome outcome = latency.evaluate(spec(EvaluationMetricType.LATENCY, BigDecimal.valueOf(1000)),
                ctx(1500L, null, null));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    void latencyPassesWithNoThresholdConfigured() {
        MetricOutcome outcome = latency.evaluate(spec(EvaluationMetricType.LATENCY, null), ctx(999999L, null, null));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void costPassesWhenUnderThreshold() {
        MetricOutcome outcome = cost.evaluate(spec(EvaluationMetricType.COST, BigDecimal.valueOf(1.0)),
                ctx(null, BigDecimal.valueOf(0.5), null));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void costFailsWhenOverThreshold() {
        MetricOutcome outcome = cost.evaluate(spec(EvaluationMetricType.COST, BigDecimal.valueOf(0.1)),
                ctx(null, BigDecimal.valueOf(0.5), null));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    void tokenCountPassesWhenUnderThreshold() {
        MetricOutcome outcome = tokenCount.evaluate(spec(EvaluationMetricType.TOKEN_COUNT, BigDecimal.valueOf(100)),
                ctx(null, null, 50));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void tokenCountFailsWhenOverThreshold() {
        MetricOutcome outcome = tokenCount.evaluate(spec(EvaluationMetricType.TOKEN_COUNT, BigDecimal.valueOf(100)),
                ctx(null, null, 500));
        assertThat(outcome.passed()).isFalse();
    }
}
