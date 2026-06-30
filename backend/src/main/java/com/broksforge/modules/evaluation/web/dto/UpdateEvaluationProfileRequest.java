package com.broksforge.modules.evaluation.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "UpdateEvaluationProfileRequest", description = "Update an evaluation profile")
public record UpdateEvaluationProfileRequest(
        @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @Valid @Size(max = 50) List<MetricSpecDto> metrics,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal passThreshold
) {
}
