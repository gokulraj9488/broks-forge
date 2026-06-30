package com.broksforge.modules.advisor.web.dto;

import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.RecommendationCategory;
import com.broksforge.modules.advisor.domain.Severity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTOs for the AI Engineering Advisor. Recommendations are computed on read,
 * so these are response-only.
 */
public final class AdvisorDtos {

    private AdvisorDtos() {
    }

    @Schema(name = "RecommendationResponse",
            description = "An actionable engineering recommendation: why, what changed, how to fix, "
                    + "expected improvement, confidence and severity")
    public record RecommendationResponse(
            RecommendationCategory category,
            String title,
            String why,
            String whatChanged,
            String howToFix,
            String expectedImprovement,
            Confidence confidence,
            Severity severity,
            List<String> evidence,
            String knowledgeKey
    ) {
    }

    @Schema(name = "SeverityCount")
    public record SeverityCount(Severity severity, long count) {
    }

    @Schema(name = "AdvisoryReportResponse", description = "A scoped set of engineering recommendations")
    public record AdvisoryReportResponse(
            String scope,
            String subject,
            int recommendationCount,
            List<SeverityCount> severityBreakdown,
            List<RecommendationResponse> recommendations,
            List<String> notes
    ) {
    }
}
