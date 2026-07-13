package com.broksforge.modules.evaluation.service.metric;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;

/**
 * One pluggable metric implementation, analogous to {@code ProviderAdapter} in the model
 * module. Each metric is a {@code @Component} registered with {@link EvaluationMetricEngine},
 * which dispatches to it by {@link EvaluationMetricType} — adding a metric never requires
 * touching the engine itself, only adding a new implementation (and, for one of the fixed
 * catalogue types, an enum constant).
 */
public interface EvaluationMetric {

    EvaluationMetricType type();

    MetricOutcome evaluate(MetricSpec spec, MetricContext context);
}
