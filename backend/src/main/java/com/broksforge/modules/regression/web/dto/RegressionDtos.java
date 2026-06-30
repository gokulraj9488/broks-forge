package com.broksforge.modules.regression.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class RegressionDtos {

    private RegressionDtos() {
    }

    @Schema(name = "CreateRegressionCheckRequest", description = "Compare a candidate evaluation job to a baseline")
    public record CreateRegressionCheckRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull UUID baselineJobId,
            @NotNull UUID candidateJobId,
            @DecimalMin("0.0") BigDecimal tolerancePct
    ) {
    }

    @Schema(name = "RegressionCheckResponse")
    public record RegressionCheckResponse(
            UUID id,
            UUID organizationId,
            UUID projectId,
            String name,
            UUID baselineJobId,
            UUID candidateJobId,
            BigDecimal tolerancePct,
            boolean regressed,
            Map<String, Object> findings,
            Instant createdAt
    ) {
    }

    @Schema(name = "RegressionCheckSummaryResponse")
    public record RegressionCheckSummaryResponse(
            UUID id,
            String name,
            UUID baselineJobId,
            UUID candidateJobId,
            boolean regressed,
            Instant createdAt
    ) {
    }
}
