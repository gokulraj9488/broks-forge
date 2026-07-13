package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Total token usage is within threshold. */
@Component
public class TokenCountMetric extends AbstractEvaluationMetric {

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.TOKEN_COUNT;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        BigDecimal threshold = spec.threshold();
        Integer measured = ctx.totalTokens();
        if (threshold == null || measured == null) {
            return outcome(spec, true, threshold, "Tokens " + (measured == null ? "not reported" : measured));
        }
        boolean ok = BigDecimal.valueOf(measured).compareTo(threshold) <= 0;
        return outcome(spec, ok, threshold, "Tokens " + measured + " vs <= " + threshold);
    }
}
