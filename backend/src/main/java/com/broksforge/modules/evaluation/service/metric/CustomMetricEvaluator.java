package com.broksforge.modules.evaluation.service.metric;

import com.broksforge.modules.evaluation.domain.MetricSpec;

/**
 * A named, pluggable metric under the {@code CUSTOM} catalogue entry — the one true
 * no-code-change extension point: registering a new {@code @Component} implementing this
 * interface makes a brand-new metric selectable via {@code params.key}, with zero changes to
 * {@link EvaluationMetricType}, {@link EvaluationMetricEngine}, or any other engine file.
 */
public interface CustomMetricEvaluator {

    /** The value profile authors put in {@code MetricSpec.params.key} to select this evaluator. */
    String key();

    MetricOutcome evaluate(MetricSpec spec, MetricContext context);
}
