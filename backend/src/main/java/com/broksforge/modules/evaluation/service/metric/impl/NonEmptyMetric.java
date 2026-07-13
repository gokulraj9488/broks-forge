package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.springframework.stereotype.Component;

/** Output is non-blank. */
@Component
public class NonEmptyMetric extends AbstractEvaluationMetric {

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.NON_EMPTY;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        boolean ok = ctx.output() != null && !ctx.output().isBlank();
        return outcome(spec, ok, null, ok ? "Output is non-empty" : "Output is empty");
    }
}
