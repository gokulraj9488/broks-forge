package com.broksforge.modules.evaluation.web.dto;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A metric configuration inside an evaluation profile. {@code params} carries
 * metric-specific options (e.g. {@code {"pattern": "^\\d+$"}} for REGEX_MATCH,
 * {@code {"min": 10, "max": 500}} for LENGTH).
 */
@Schema(name = "MetricSpec", description = "A configured metric within an evaluation profile")
public record MetricSpecDto(
        @NotNull EvaluationMetricType type,
        @Size(max = 120) String label,
        BigDecimal weight,
        BigDecimal threshold,
        Map<String, Object> params
) {
}
