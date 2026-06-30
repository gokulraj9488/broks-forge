package com.broksforge.modules.dashboard.web.dto;

import com.broksforge.modules.analytics.web.dto.AnalyticsDtos.AnalyticsOverviewResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.regression.web.dto.RegressionDtos.RegressionCheckSummaryResponse;
import com.broksforge.modules.report.web.dto.ReportDtos.ReportResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    @Schema(name = "DashboardCounts")
    public record DashboardCounts(
            long agents,
            long datasets,
            long prompts,
            long evaluationJobs,
            long runningJobs,
            long benchmarks
    ) {
    }

    @Schema(name = "TopAgent", description = "An agent ranked by recent evaluation pass rate")
    public record TopAgent(UUID agentId, double avgPassRate, int evaluatedJobs) {
    }

    @Schema(name = "DashboardResponse", description = "Project dashboard roll-up")
    public record DashboardResponse(
            DashboardCounts counts,
            List<EvaluationJobResponse> recentJobs,
            AnalyticsOverviewResponse analytics,
            List<ReportResponse> recentReports,
            List<RegressionCheckSummaryResponse> regressionAlerts,
            List<TopAgent> topAgents
    ) {
    }
}
