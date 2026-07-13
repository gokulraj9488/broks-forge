package com.broksforge.modules.evaluation.service;

import com.broksforge.config.properties.EvaluationProperties;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationJobEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Automatic failure recovery for the background evaluation engine: a RUNNING job whose
 * {@code lastProgressAt} heartbeat has gone stale (the app crashed or was redeployed mid-run) is
 * resumed automatically, continuing from whatever the last checkpoint left behind. Runs on a
 * fixed delay rather than a cron so the interval only ever measures from the previous poll's
 * completion, avoiding overlap if a poll ever runs long.
 */
@Slf4j
@Component
public class EvaluationJobRecoveryScheduler {

    private final EvaluationJobLifecycle lifecycle;
    private final EvaluationJobEventService events;
    private final EvaluationBackgroundRunner runner;
    private final EvaluationProperties properties;

    public EvaluationJobRecoveryScheduler(EvaluationJobLifecycle lifecycle, EvaluationJobEventService events,
                                          EvaluationBackgroundRunner runner, EvaluationProperties properties) {
        this.lifecycle = lifecycle;
        this.events = events;
        this.runner = runner;
        this.properties = properties;
    }

    /** Polls at a fixed 60s cadence regardless of the configured stall threshold, which only
     * affects how old a heartbeat must be to count as stalled (checked in the method body). */
    @Scheduled(fixedDelay = 60_000)
    public void recoverStalledJobs() {
        Instant threshold = Instant.now().minusMillis(properties.stallAfterMs());
        List<EvaluationJob> stalled = lifecycle.findStalledRunning(threshold);
        for (EvaluationJob job : stalled) {
            log.warn("Evaluation job {} appears stalled (no progress since {}); resuming", job.getId(),
                    job.getLastProgressAt());
            try {
                int attempt = lifecycle.resumeForBackground(job.getId());
                events.record(job.getId(), job.getOrganizationId(), EvaluationJobEventType.STALL_RECOVERED,
                        "Resumed after a stalled heartbeat (pass %d)".formatted(attempt));
                runner.runAsync(job.getId(), attempt);
            } catch (RuntimeException e) {
                log.error("Failed to auto-resume stalled evaluation job {}", job.getId(), e);
            }
        }
    }
}
