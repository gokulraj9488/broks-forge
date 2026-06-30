package com.broksforge.modules.analytics.service;

import com.broksforge.modules.analytics.web.dto.AnalyticsDtos.AnalyticsOverviewResponse;
import com.broksforge.modules.analytics.web.dto.AnalyticsDtos.AnalyticsTrendPoint;
import com.broksforge.modules.evaluation.service.EvaluationAnalyticsService;
import com.broksforge.modules.evaluation.service.EvaluationAnalyticsSummary;
import com.broksforge.modules.evaluation.service.EvaluationTrendPoint;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Composes evaluation analytics (owned by the evaluation module) into the
 * presentation shapes the UI consumes. Cross-tenant safety is enforced inside the
 * underlying {@link EvaluationAnalyticsService} queries.
 */
@Service
public class AnalyticsService {

    private static final int MIN_WINDOW = 1;
    private static final int MAX_WINDOW = 365;

    private final EvaluationAnalyticsService evaluationAnalytics;

    public AnalyticsService(EvaluationAnalyticsService evaluationAnalytics) {
        this.evaluationAnalytics = evaluationAnalytics;
    }

    public AnalyticsOverviewResponse overview(UUID actorId, UUID organizationId, UUID projectId, int windowDays) {
        int window = Math.max(MIN_WINDOW, Math.min(MAX_WINDOW, windowDays));
        Instant from = Instant.now().minus(Duration.ofDays(window));

        EvaluationAnalyticsSummary summary =
                evaluationAnalytics.summary(actorId, organizationId, projectId, from);
        List<AnalyticsTrendPoint> trend =
                evaluationAnalytics.dailyTrend(actorId, organizationId, projectId, from).stream()
                        .map(this::toTrendPoint)
                        .toList();

        return new AnalyticsOverviewResponse(
                window,
                summary.jobCount(),
                summary.runCount(),
                summary.passedCount(),
                summary.passRate(),
                summary.avgLatencyMs(),
                summary.totalTokens(),
                summary.totalCost(),
                trend);
    }

    private AnalyticsTrendPoint toTrendPoint(EvaluationTrendPoint point) {
        return new AnalyticsTrendPoint(point.date(), point.runCount(), point.avgLatencyMs(),
                point.totalTokens(), point.totalCost());
    }
}
