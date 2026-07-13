package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.CustomMetricEvaluator;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dispatches {@code CUSTOM}-type specs to a named {@link CustomMetricEvaluator} bean via
 * {@code params.key} — the extension point that needs no {@link EvaluationMetricType} change
 * at all for a genuinely new metric, only a new evaluator bean.
 */
@Component
public class CustomMetric extends AbstractEvaluationMetric {

    private final Map<String, CustomMetricEvaluator> byKey;

    public CustomMetric(List<CustomMetricEvaluator> evaluators) {
        this.byKey = evaluators.stream().collect(Collectors.toMap(CustomMetricEvaluator::key, e -> e));
    }

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.CUSTOM;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        String key = strParam(spec, "key");
        if (key == null || key.isBlank()) {
            return outcome(spec, false, spec.threshold(), "No custom metric key configured (params.key)");
        }
        CustomMetricEvaluator evaluator = byKey.get(key);
        if (evaluator == null) {
            return outcome(spec, false, spec.threshold(), "No custom metric evaluator registered for key '" + key + "'");
        }
        return evaluator.evaluate(spec, ctx);
    }
}
