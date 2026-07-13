package com.broksforge.modules.evaluation.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns the short, independent transactions around an evaluation job's status
 * transitions, deliberately separate from the long-running (transaction-free)
 * execution loop. This is what lets the executor make network calls without holding
 * a database connection.
 */
@Service
public class EvaluationJobLifecycle {

    private final EvaluationJobRepository jobRepository;

    public EvaluationJobLifecycle(EvaluationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public EvaluationJob save(EvaluationJob job) {
        return jobRepository.save(job);
    }

    /**
     * Hard-deletes a just-created PENDING job that failed its auto-run dispatch before any run
     * was ever recorded against it — used only to make "create with autoRun" atomic from the
     * caller's perspective (either it ends up PENDING/RUNNING, or nothing persists at all). Safe
     * because a job that never left PENDING has no {@code evaluation_runs} or benchmark entries
     * referencing it yet.
     */
    @Transactional
    public void discardUnrun(UUID jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() == EvaluationStatus.PENDING) {
                jobRepository.delete(job);
            }
        });
    }

    @Transactional
    public void markRunning(UUID jobId) {
        EvaluationJob job = load(jobId);
        if (job.getStatus() != EvaluationStatus.PENDING) {
            throw new ResourceConflictException(ErrorCode.EVALUATION_JOB_NOT_RUNNABLE,
                    "Evaluation job is not in a runnable (PENDING) state");
        }
        job.setStatus(EvaluationStatus.RUNNING);
        job.setStartedAt(Instant.now());
    }

    @Transactional
    public void complete(UUID jobId, EvaluationOutcome outcome) {
        EvaluationJob job = load(jobId);
        job.setStatus(EvaluationStatus.COMPLETED);
        job.setCompletedItems(outcome.completed());
        job.setFailedItems(outcome.failed());
        job.setSummary(outcome.summary());
        job.setCompletedAt(Instant.now());
    }

    @Transactional
    public void fail(UUID jobId, String message) {
        EvaluationJob job = load(jobId);
        job.setStatus(EvaluationStatus.FAILED);
        job.setErrorMessage(message == null ? "Execution failed"
                : (message.length() <= 1000 ? message : message.substring(0, 1000)));
        job.setCompletedAt(Instant.now());
    }

    /** Accepts a job for background execution: RUNNING, queued now, pinned to its batch size. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void queueForBackground(UUID jobId, int batchSize) {
        EvaluationJob job = load(jobId);
        if (job.getStatus() != EvaluationStatus.PENDING) {
            throw new ResourceConflictException(ErrorCode.EVALUATION_JOB_NOT_RUNNABLE,
                    "Evaluation job is not in a runnable (PENDING) state");
        }
        job.setStatus(EvaluationStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setQueuedAt(Instant.now());
        job.setBatchSize(batchSize);
        job.setLastProgressAt(Instant.now());
    }

    /**
     * Re-arms a job for another background pass: after it was interrupted (crash, status still
     * RUNNING but stalled), ended in FAILED/CANCELLED with items outstanding, or COMPLETED with
     * some rows failed (the common "retry just the failed rows" case). Bumps the retry/pass
     * counter. Rejects a COMPLETED job with nothing outstanding — there is nothing to resume.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int resumeForBackground(UUID jobId) {
        EvaluationJob job = load(jobId);
        boolean resumable = job.getStatus() == EvaluationStatus.FAILED
                || job.getStatus() == EvaluationStatus.CANCELLED
                || job.getStatus() == EvaluationStatus.RUNNING
                || (job.getStatus() == EvaluationStatus.COMPLETED && job.getFailedItems() > 0);
        if (!resumable) {
            throw new ResourceConflictException(ErrorCode.EVALUATION_JOB_NOT_RUNNABLE,
                    "Only a failed, cancelled, stalled running, or partially-failed completed job can be resumed");
        }
        job.setStatus(EvaluationStatus.RUNNING);
        job.setErrorMessage(null);
        job.setLastProgressAt(Instant.now());
        job.setRetryCount(job.getRetryCount() + 1);
        return job.getRetryCount() + 1; // +1: the new attempt/pass number for runs created by this pass
    }

    /**
     * Resets the live progress counters to a known-correct baseline before a (re)pass begins:
     * {@code completedItems} to the number of items that already have a succeeded run, and
     * {@code failedItems} to zero. Without this, resuming a job whose earlier pass left some
     * items FAILED would double-count them the moment this pass's checkpoints add their own delta
     * on top of the stale count from the previous pass.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetProgressCounters(UUID jobId, int alreadySucceededCount) {
        EvaluationJob job = load(jobId);
        job.setCompletedItems(alreadySucceededCount);
        job.setFailedItems(0);
    }

    /** Persists incremental progress mid-run, so GET reflects live progress and a crash resumes from here. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkpoint(UUID jobId, int completedDelta, int failedDelta) {
        EvaluationJob job = load(jobId);
        job.setCompletedItems(job.getCompletedItems() + completedDelta);
        job.setFailedItems(job.getFailedItems() + failedDelta);
        job.setLastProgressAt(Instant.now());
    }

    /** Finalizes a background job from a DB-computed summary (never holds all outcomes in memory). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeBackground(UUID jobId, java.util.Map<String, Object> summary, int completedItems,
                                   int failedItems) {
        EvaluationJob job = load(jobId);
        job.setStatus(EvaluationStatus.COMPLETED);
        job.setSummary(summary);
        job.setCompletedItems(completedItems);
        job.setFailedItems(failedItems);
        job.setCompletedAt(Instant.now());
        job.setLastProgressAt(Instant.now());
    }

    /** Reads current status without starting a long-lived transaction (used for cooperative cancel checks). */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public EvaluationStatus currentStatus(UUID jobId) {
        return load(jobId).getStatus();
    }

    /** Jobs whose last heartbeat is older than the stall threshold — candidates for automatic recovery. */
    @Transactional(readOnly = true)
    public List<EvaluationJob> findStalledRunning(Instant olderThan) {
        return jobRepository.findByStatusAndLastProgressAtBeforeAndDeletedFalse(EvaluationStatus.RUNNING, olderThan);
    }

    private EvaluationJob load(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> ResourceNotFoundException.of("Evaluation job", jobId));
    }
}
