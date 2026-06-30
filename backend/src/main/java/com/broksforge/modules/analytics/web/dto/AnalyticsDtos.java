package com.broksforge.modules.analytics.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    @Schema(name = "AnalyticsTrendPoint", description = "One day of evaluation telemetry")
    public record AnalyticsTrendPoint(
            Instant date,
            long runCount,
            Double avgLatencyMs,
            long totalTokens,
            BigDecimal totalCost
    ) {
    }

    @Schema(name = "AnalyticsOverviewResponse",
            description = "Cost, latency, token and usage analytics over a time window with a daily trend")
    public record AnalyticsOverviewResponse(
            int windowDays,
            long jobCount,
            long runCount,
            long passedCount,
            double passRate,
            Double avgLatencyMs,
            long totalTokens,
            BigDecimal totalCost,
            List<AnalyticsTrendPoint> trend
    ) {
    }
}
