package com.broksforge.modules.report.web.dto;

import com.broksforge.modules.report.domain.ReportFormat;
import com.broksforge.modules.report.domain.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    @Schema(name = "GenerateReportRequest", description = "Generate and download a report export")
    public record GenerateReportRequest(
            @NotNull ReportType type,
            @NotNull ReportFormat format,
            @NotNull UUID targetId,
            @Size(max = 200) String name
    ) {
    }

    @Schema(name = "ReportResponse", description = "Audit record of a generated report")
    public record ReportResponse(
            UUID id,
            String name,
            ReportType type,
            ReportFormat format,
            UUID targetId,
            UUID ownerId,
            Instant createdAt
    ) {
    }
}
