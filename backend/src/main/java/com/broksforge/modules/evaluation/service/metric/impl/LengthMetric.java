package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.springframework.stereotype.Component;

/** Output length is within {@code [params.min, params.max]} characters. */
@Component
public class LengthMetric extends AbstractEvaluationMetric {

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.LENGTH;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        int len = nullToEmpty(ctx.output()).length();
        Integer min = intParam(spec, "min");
        Integer max = intParam(spec, "max");
        boolean ok = (min == null || len >= min) && (max == null || len <= max);
        return outcome(spec, ok, null, "Output length " + len
                + " (min=" + (min == null ? "-" : min) + ", max=" + (max == null ? "-" : max) + ")");
    }
}
