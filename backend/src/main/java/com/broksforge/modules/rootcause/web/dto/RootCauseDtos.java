package com.broksforge.modules.rootcause.web.dto;

import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.Severity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTOs for root-cause analysis. Findings are computed on read, so these are
 * response-only.
 */
public final class RootCauseDtos {

    private RootCauseDtos() {
    }

    @Schema(name = "RootCauseFindingResponse",
            description = "A diagnosed cause: root cause, evidence, confidence, recommendation, "
                    + "expected improvement and severity")
    public record RootCauseFindingResponse(
            String rootCause,
            Severity severity,
            Confidence confidence,
            List<String> evidence,
            String recommendation,
            String expectedImprovement,
            String knowledgeKey
    ) {
    }

    @Schema(name = "RootCauseReportResponse", description = "Root-cause analysis for a job or regression")
    public record RootCauseReportResponse(
            String scope,
            String subject,
            int findingCount,
            List<RootCauseFindingResponse> findings,
            List<String> notes
    ) {
    }
}
