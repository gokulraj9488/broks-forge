package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Invocation cost is within threshold. */
@Component
public class CostMetric extends AbstractEvaluationMetric {

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.COST;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        BigDecimal threshold = spec.threshold();
        BigDecimal measured = ctx.cost();
        if (threshold == null || measured == null) {
            return outcome(spec, true, threshold, "Cost " + (measured == null ? "not reported" : measured.toPlainString()));
        }
        boolean ok = measured.compareTo(threshold) <= 0;
        return outcome(spec, ok, threshold, "Cost " + measured.toPlainString() + " vs <= " + threshold.toPlainString());
    }
}
