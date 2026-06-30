package com.broksforge.modules.evaluation.service;

import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.evaluation.repository.EvaluationRunAggregate;
import com.broksforge.modules.evaluation.repository.EvaluationRunRepository;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Published analytics queries over evaluation data. Owning these here (rather than
 * letting the analytics module touch evaluation tables) keeps persistence ownership
 * inside the evaluation module; the analytics module composes and presents them.
 */
@Service
public class EvaluationAnalyticsService {

    private final EvaluationJobRepository jobRepository;
    private final EvaluationRunRepository runRepository;
    private final OrganizationAccessService accessService;

    public EvaluationAnalyticsService(EvaluationJobRepository jobRepository,
                                      EvaluationRunRepository runRepository,
                                      OrganizationAccessService accessService) {
        this.jobRepository = jobRepository;
        this.runRepository = runRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public EvaluationAnalyticsSummary summary(UUID actorId, UUID organizationId, UUID projectId, Instant from) {
        accessService.requireMembership(organizationId, actorId);
        long jobCount = jobRepository.countByProjectIdAndDeletedFalse(projectId);
        EvaluationRunAggregate aggregate = runRepository.aggregate(organizationId, projectId, from);

        long runCount = nullToZero(aggregate.runCount());
        long passed = nullToZero(aggregate.passedCount());
        double passRate = runCount > 0 ? round((double) passed / runCount) : 0.0;
        long totalTokens = nullToZero(aggregate.totalTokens());
        BigDecimal totalCost = aggregate.totalCost() != null ? aggregate.totalCost() : BigDecimal.ZERO;
        Double avgLatency = aggregate.avgLatencyMs() != null ? round(aggregate.avgLatencyMs()) : null;

        return new EvaluationAnalyticsSummary(jobCount, runCount, passed, passRate, avgLatency, totalTokens, totalCost);
    }

    @Transactional(readOnly = true)
    public List<EvaluationTrendPoint> dailyTrend(UUID actorId, UUID organizationId, UUID projectId, Instant from) {
        accessService.requireMembership(organizationId, actorId);
        List<Object[]> rows = runRepository.dailyTrend(organizationId, projectId, from);
        List<EvaluationTrendPoint> points = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            points.add(new EvaluationTrendPoint(
                    toInstant(row[0]),
                    toLong(row[1]),
                    row[2] == null ? null : round(((Number) row[2]).doubleValue()),
                    toLong(row[3]),
                    toBigDecimal(row[4])));
        }
        return points;
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return null;
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
