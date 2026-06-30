package com.broksforge.modules.evaluation.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "EvaluationProfileResponse", description = "A reusable evaluation profile")
public record EvaluationProfileResponse(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String name,
        String slug,
        String description,
        UUID ownerId,
        List<MetricSpecDto> metrics,
        BigDecimal passThreshold,
        Instant createdAt,
        Instant updatedAt
) {
}
