package com.broksforge.modules.evaluation.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A declarative metric configuration within an {@link EvaluationProfile}: which
 * metric to compute, an optional display label, an optional weight (for weighted
 * scoring), an optional threshold (for performance/cost metrics), and free-form
 * params (e.g. a regex pattern or a substring). Stored as JSON, so new metric
 * parameters never require a migration.
 */
public record MetricSpec(
        EvaluationMetricType type,
        String label,
        BigDecimal weight,
        BigDecimal threshold,
        Map<String, Object> params
) {
    public MetricSpec {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public Map<String, Object> paramsOrEmpty() {
        return params;
    }
}
