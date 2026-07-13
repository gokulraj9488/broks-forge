package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Invocation latency is within threshold milliseconds. */
@Component
public class LatencyMetric extends AbstractEvaluationMetric {

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.LATENCY;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        BigDecimal threshold = spec.threshold();
        Long measured = ctx.latencyMs();
        if (threshold == null) {
            return outcome(spec, true, null, "Latency " + (measured == null ? "n/a" : measured + "ms"));
        }
        boolean ok = measured == null || BigDecimal.valueOf(measured).compareTo(threshold) <= 0;
        return outcome(spec, ok, threshold,
                "Latency " + (measured == null ? "n/a" : measured + "ms") + " vs <= " + threshold + "ms");
    }
}
