package com.broksforge.modules.evaluation.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    private EvaluationJob load(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> ResourceNotFoundException.of("Evaluation job", jobId));
    }
}
