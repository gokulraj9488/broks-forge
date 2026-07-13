package com.broksforge.modules.evaluation.service;

import com.broksforge.config.properties.EvaluationProperties;
import com.broksforge.modules.dataset.service.DatasetRow;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationJobEventType;
import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.EvaluationRunStatus;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.evaluation.repository.EvaluationResultRepository;
import com.broksforge.modules.evaluation.repository.EvaluationRunAggregate;
import com.broksforge.modules.evaluation.repository.EvaluationRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * The background execution engine for evaluation jobs whose dataset exceeds
 * {@code broksforge.evaluation.max-items-per-job}. Streams the dataset page by page (never
 * holding the whole thing in memory), processes each page's rows with bounded concurrency,
 * checkpoints progress after every page (so a crash mid-run loses at most one page's work), and
 * cooperatively stops if the job is cancelled. Items that already have a succeeded run — from an
 * earlier pass — are skipped, which is what makes {@link EvaluationService#resume} both "resume
 * an interrupted job" and "retry failed rows only" the same code path.
 */
@Slf4j
@Component
public class EvaluationBackgroundRunner {

    private final EvaluationJobRepository jobRepository;
    private final EvaluationJobLifecycle lifecycle;
    private final EvaluationJobEventService events;
    private final EvaluationPlanBuilder planBuilder;
    private final EvaluationJobExecutor executor;
    private final DatasetService datasetService;
    private final EvaluationRunRepository runRepository;
    private final EvaluationResultRepository resultRepository;
    private final EvaluationProperties properties;
    private final Executor rowExecutor;

    /**
     * Jobs with a background pass currently executing on this instance. {@code markRunning}/
     * {@code queueForBackground}/{@code resumeForBackground} only guard the persisted status
     * column — {@code resumeForBackground} deliberately allows RUNNING as a resumable status (to
     * recover a genuinely crashed pass), so the DB alone cannot distinguish "crashed, safe to
     * restart" from "still alive, just hasn't checkpointed yet." The recovery scheduler resuming a
     * job whose {@code lastProgressAt} looks stale while a slow-but-alive worker is mid-batch would
     * otherwise dispatch a second concurrent {@code runAsync} for the same job, double-processing
     * rows. This in-memory guard makes {@code runAsync} single-flight per job id on this instance —
     * the correct scope given this deployment has no clustering/leader-election; a horizontally
     * scaled deployment would need a DB-backed lease instead.
     */
    private final Set<UUID> inFlightJobIds = ConcurrentHashMap.newKeySet();

    public EvaluationBackgroundRunner(EvaluationJobRepository jobRepository, EvaluationJobLifecycle lifecycle,
                                      EvaluationJobEventService events, EvaluationPlanBuilder planBuilder,
                                      EvaluationJobExecutor executor, DatasetService datasetService,
                                      EvaluationRunRepository runRepository,
                                      EvaluationResultRepository resultRepository, EvaluationProperties properties,
                                      @Qualifier("evaluationRowExecutor") Executor rowExecutor) {
        this.jobRepository = jobRepository;
        this.lifecycle = lifecycle;
        this.events = events;
        this.planBuilder = planBuilder;
        this.executor = executor;
        this.datasetService = datasetService;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.properties = properties;
        this.rowExecutor = rowExecutor;
    }

    @Async("evaluationJobTaskExecutor")
    public void runAsync(UUID jobId, int attempt) {
        if (!inFlightJobIds.add(jobId)) {
            log.warn("Evaluation job {} already has a background pass in flight on this instance; "
                    + "skipping duplicate dispatch (requested pass {})", jobId, attempt);
            return;
        }
        try {
            runAsyncInternal(jobId, attempt);
        } finally {
            inFlightJobIds.remove(jobId);
        }
    }

    private void runAsyncInternal(UUID jobId, int attempt) {
        EvaluationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Evaluation job {} vanished before background execution started", jobId);
            return;
        }
        events.record(jobId, job.getOrganizationId(), EvaluationJobEventType.STARTED,
                "Background execution started (pass %d)".formatted(attempt));

        ExecutionContext context;
        try {
            context = planBuilder.buildContext(job.getOwnerId(), job);
        } catch (RuntimeException e) {
            log.error("Evaluation job {} failed to resolve execution context", jobId, e);
            lifecycle.fail(jobId, e.getMessage());
            events.record(jobId, job.getOrganizationId(), EvaluationJobEventType.FAILED, safeMessage(e));
            return;
        }

        int batchSize = job.getBatchSize() != null ? job.getBatchSize() : properties.batchSize();
        Set<UUID> alreadySucceeded = runRepository.findItemIdsByEvaluationJobIdAndStatus(
                jobId, EvaluationRunStatus.SUCCEEDED);
        lifecycle.resetProgressCounters(jobId, alreadySucceeded.size());

        boolean cancelled;
        try {
            cancelled = processPages(job, context, batchSize, attempt, alreadySucceeded);
        } catch (RuntimeException e) {
            log.error("Evaluation job {} failed during background execution", jobId, e);
            lifecycle.fail(jobId, e.getMessage());
            events.record(jobId, job.getOrganizationId(), EvaluationJobEventType.FAILED, safeMessage(e));
            return;
        }

        if (cancelled) {
            events.record(jobId, job.getOrganizationId(), EvaluationJobEventType.CANCELLED,
                    "Background execution stopped: job was cancelled");
            return;
        }

        Map<String, Object> summary = buildSummaryFromDb(jobId, job.getTotalItems());
        int succeeded = ((Number) summary.get("succeeded")).intValue();
        int failed = ((Number) summary.get("failed")).intValue();
        lifecycle.completeBackground(jobId, summary, succeeded, failed);
        events.record(jobId, job.getOrganizationId(), EvaluationJobEventType.COMPLETED,
                "Background execution completed: %d/%d items succeeded".formatted(succeeded, job.getTotalItems()));
    }

    /** @return true if the run stopped early because the job was cancelled */
    private boolean processPages(EvaluationJob job, ExecutionContext context, int batchSize, int attempt,
                                 Set<UUID> alreadySucceeded) {
        UUID jobId = job.getId();
        EvaluationPlan plan = context.toPlan(List.of());
        int page = 0;
        while (true) {
            if (lifecycle.currentStatus(jobId) == EvaluationStatus.CANCELLED) {
                return true;
            }

            Page<DatasetRow> rows = datasetService.loadExecutionItemsPage(job.getOwnerId(), job.getOrganizationId(),
                    job.getProjectId(), job.getDatasetId(), job.getDatasetVersionId(),
                    PageRequest.of(page, batchSize));
            if (rows.isEmpty()) {
                return false;
            }

            List<DatasetRow> toProcess = rows.getContent().stream()
                    .filter(row -> !alreadySucceeded.contains(row.itemId()))
                    .toList();

            if (!toProcess.isEmpty()) {
                List<CompletableFuture<RunTotals>> futures = toProcess.stream()
                        .map(row -> CompletableFuture.supplyAsync(() -> executor.executeRow(plan, row,
                                row.sequence(), attempt, properties.maxAttempts(), properties.retryBackoffMs(),
                                properties.retryBackoffMaxMs()), rowExecutor))
                        .toList();

                int completedDelta = 0;
                int failedDelta = 0;
                for (CompletableFuture<RunTotals> future : futures) {
                    RunTotals totals = future.join();
                    if (totals.success()) {
                        completedDelta++;
                    } else {
                        failedDelta++;
                    }
                }
                lifecycle.checkpoint(jobId, completedDelta, failedDelta);
                events.record(jobId, job.getOrganizationId(), EvaluationJobEventType.CHECKPOINT,
                        "Batch %d: +%d succeeded, +%d failed".formatted(page, completedDelta, failedDelta));
            }

            if (!rows.hasNext()) {
                return false;
            }
            page++;
        }
    }

    /**
     * Mirrors {@code EvaluationJobExecutor.buildSummary}'s exact key shape, but reconstructed from
     * DB aggregates instead of an in-memory {@code Accumulator} — a background pass can span many
     * checkpoints (and even resume across a restart), so there is no single in-memory accumulator
     * to read at the end. Consumers (Run Results UI, benchmarking, regression, root-cause) must see
     * the same keys regardless of which path ran the job.
     */
    private Map<String, Object> buildSummaryFromDb(UUID jobId, int totalItems) {
        EvaluationRunAggregate agg = runRepository.aggregateForJob(jobId, EvaluationRunStatus.SUCCEEDED);
        long succeeded = agg.runCount() == null ? 0 : agg.runCount();
        long passed = agg.passedCount() == null ? 0 : agg.passedCount();
        long skipped = resultRepository.countSkippedRuns(jobId);
        long failed = Math.max(0, succeeded - passed - skipped);
        BigDecimal avgScore = runRepository.averageScoreForJob(jobId, EvaluationRunStatus.SUCCEEDED);

        List<MetricFailureTally> passFailTallies = resultRepository.tallyByMetric(jobId);
        List<MetricExecutionFailureTally> execFailTallies = resultRepository.tallyExecutionFailuresByMetric(jobId);
        long completedMetricCount = passFailTallies.stream()
                .mapToLong(t -> t.passedOrZero() + t.failedOrZero()).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", totalItems);
        summary.put("succeeded", succeeded);
        summary.put("failed", Math.max(0, totalItems - succeeded));
        // Kept for backward compatibility with existing consumers, mirroring the sync path.
        summary.put("passed", passed);
        long evaluated = passed + failed;
        summary.put("passRate", evaluated > 0 ? round((double) passed / evaluated) : 0.0);
        summary.put("avgLatencyMs", agg.avgLatencyMs() == null ? null : round(agg.avgLatencyMs()));
        summary.put("totalTokens", agg.totalTokens() == null ? 0L : agg.totalTokens());
        summary.put("totalCost", (agg.totalCost() == null ? BigDecimal.ZERO : agg.totalCost()).doubleValue());
        summary.put("avgScore", avgScore == null ? 0.0 : avgScore.setScale(4, RoundingMode.HALF_UP).doubleValue());
        summary.put("completedMetricCount", completedMetricCount);

        Map<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("passed", passed);
        evaluation.put("failed", failed);
        evaluation.put("skipped", skipped);
        summary.put("evaluation", evaluation);

        Map<MetricExecutionStatus, Long> executionErrorTotals = new EnumMap<>(MetricExecutionStatus.class);
        for (MetricExecutionStatus status : EvaluationJobExecutor.ERROR_STATUSES) {
            executionErrorTotals.put(status, 0L);
        }
        for (MetricExecutionFailureTally tally : execFailTallies) {
            executionErrorTotals.merge(tally.status(), tally.countOrZero(), Long::sum);
        }
        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("succeeded", completedMetricCount);
        execution.put("authenticationErrors", executionErrorTotals.get(MetricExecutionStatus.AUTHENTICATION_ERROR));
        execution.put("providerErrors", executionErrorTotals.get(MetricExecutionStatus.PROVIDER_UNAVAILABLE));
        execution.put("rateLimited", executionErrorTotals.get(MetricExecutionStatus.RATE_LIMITED));
        execution.put("modelNotFound", executionErrorTotals.get(MetricExecutionStatus.MODEL_NOT_FOUND));
        execution.put("timeouts", executionErrorTotals.get(MetricExecutionStatus.TIMEOUT));
        execution.put("infrastructureErrors", executionErrorTotals.get(MetricExecutionStatus.INFRASTRUCTURE_ERROR));
        summary.put("execution", execution);
        long unavailableMetricCount = executionErrorTotals.values().stream().mapToLong(Long::longValue).sum();
        summary.put("unavailableMetricCount", unavailableMetricCount);

        Map<String, Object> metricPassRates = new LinkedHashMap<>();
        for (MetricFailureTally tally : passFailTallies) {
            long total = tally.passedOrZero() + tally.failedOrZero();
            metricPassRates.put(tally.metricType().name(), total > 0 ? round((double) tally.passedOrZero() / total) : 0.0);
        }
        summary.put("metricPassRates", metricPassRates);

        Set<EvaluationMetricType> metricTypes = new LinkedHashSet<>();
        passFailTallies.forEach(t -> metricTypes.add(t.metricType()));
        execFailTallies.forEach(t -> metricTypes.add(t.metricType()));
        Map<String, Object> metricBreakdown = new LinkedHashMap<>();
        for (EvaluationMetricType type : metricTypes) {
            long completedCount = passFailTallies.stream().filter(t -> t.metricType() == type)
                    .mapToLong(t -> t.passedOrZero() + t.failedOrZero()).findFirst().orElse(0);
            long passedCount = passFailTallies.stream().filter(t -> t.metricType() == type)
                    .mapToLong(MetricFailureTally::passedOrZero).findFirst().orElse(0);
            Map<String, Long> errors = new LinkedHashMap<>();
            for (MetricExecutionFailureTally tally : execFailTallies) {
                if (tally.metricType() == type && tally.countOrZero() > 0) {
                    errors.put(tally.status().name(), tally.countOrZero());
                }
            }
            long total = completedCount + errors.values().stream().mapToLong(Long::longValue).sum();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("total", total);
            entry.put("completed", completedCount);
            entry.put("passed", passedCount);
            entry.put("failed", completedCount - passedCount);
            entry.put("executionErrors", errors);
            metricBreakdown.put(type.name(), entry);
        }
        summary.put("metricBreakdown", metricBreakdown);

        return summary;
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null ? "Execution failed" : message;
    }
}
