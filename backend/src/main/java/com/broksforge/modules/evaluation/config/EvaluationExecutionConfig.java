package com.broksforge.modules.evaluation.config;

import com.broksforge.config.properties.EvaluationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread pools for the background evaluation engine (see {@code EvaluationBackgroundRunner}).
 *
 * <p>Two independent concurrency knobs, both configurable via {@link EvaluationProperties}:
 * {@code evaluationJobTaskExecutor} bounds how many jobs run at once — jobs beyond that queue FIFO
 * (a job's {@code priority} field is persisted and returned by the API for a future preemptive
 * scheduler, but this pool dispatches in submission order for now) — and {@code evaluationRowExecutor}
 * bounds how many row invocations run at once across <em>all</em> jobs, a single global knob rather
 * than a per-job pool, which keeps outbound call volume predictable regardless of how many jobs
 * happen to be running.</p>
 *
 * <p>Named {@code evaluationJobTaskExecutor} (not {@code evaluationJobExecutor}) to avoid colliding
 * with the {@code EvaluationJobExecutor} component bean, whose default Spring bean name is the
 * same string with a lowercase first letter.</p>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class EvaluationExecutionConfig {

    @Bean
    public ThreadPoolTaskExecutor evaluationJobTaskExecutor(EvaluationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("eval-job-");
        int concurrency = Math.max(1, properties.maxConcurrentJobs());
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(1000);
        executor.initialize();
        return executor;
    }

    /** Bounds concurrent row invocations across all background jobs at once — the pool size is the limit. */
    @Bean
    public ThreadPoolTaskExecutor evaluationRowExecutor(EvaluationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("eval-row-");
        int concurrency = Math.max(1, properties.workerConcurrency());
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(10_000);
        executor.initialize();
        return executor;
    }
}
