package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Evaluation execution configuration.
 *
 * @param maxItemsPerJob     jobs at or below this size run synchronously on the request
 *                           thread (unchanged Phase 3 behaviour); larger jobs are executed
 *                           in the background by {@code EvaluationBackgroundRunner}
 * @param batchSize          default page size used to stream dataset rows for a background job
 * @param workerConcurrency  max concurrent row invocations across all background jobs
 * @param maxConcurrentJobs  max evaluation jobs the background runner executes at once
 * @param maxAttempts        max invocation attempts per row before it is recorded as failed
 * @param retryBackoffMs     base backoff delay before retrying a transient invocation failure
 * @param retryBackoffMaxMs  cap on the exponential backoff delay
 * @param stallAfterMs       a RUNNING job with no progress for this long is considered stalled
 *                           and eligible for automatic recovery (crash resume)
 */
@Validated
@ConfigurationProperties(prefix = "broksforge.evaluation")
public record EvaluationProperties(
        int maxItemsPerJob,
        int batchSize,
        int workerConcurrency,
        int maxConcurrentJobs,
        int maxAttempts,
        long retryBackoffMs,
        long retryBackoffMaxMs,
        long stallAfterMs) {
}
