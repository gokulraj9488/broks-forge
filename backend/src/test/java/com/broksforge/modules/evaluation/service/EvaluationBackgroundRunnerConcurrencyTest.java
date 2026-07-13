package com.broksforge.modules.evaluation.service;

import com.broksforge.config.properties.EvaluationProperties;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.evaluation.repository.EvaluationResultRepository;
import com.broksforge.modules.evaluation.repository.EvaluationRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the single-flight guard added to {@link EvaluationBackgroundRunner#runAsync} — the fix
 * for the TOCTOU gap where {@code resumeForBackground} legitimately allows resuming a RUNNING job
 * (to recover a genuinely crashed pass), so the persisted status column alone cannot stop the
 * recovery scheduler from dispatching a second concurrent pass against a job whose earlier pass is
 * merely slow, not dead. Without this guard, two passes would process the same rows concurrently.
 */
@DisplayName("EvaluationBackgroundRunner (single-flight per job id)")
class EvaluationBackgroundRunnerConcurrencyTest {

    @Test
    @DisplayName("a second dispatch for a job already in flight is skipped without touching the repository")
    void skipsDuplicateDispatchForAnInFlightJob() throws Exception {
        EvaluationJobRepository jobRepository = mock(EvaluationJobRepository.class);
        EvaluationBackgroundRunner runner = new EvaluationBackgroundRunner(
                jobRepository,
                mock(EvaluationJobLifecycle.class),
                mock(EvaluationJobEventService.class),
                mock(EvaluationPlanBuilder.class),
                mock(EvaluationJobExecutor.class),
                mock(DatasetService.class),
                mock(EvaluationRunRepository.class),
                mock(EvaluationResultRepository.class),
                new EvaluationProperties(500, 50, 4, 4, 3, 1000, 30000, 300000),
                (Executor) Runnable::run);

        UUID jobId = UUID.randomUUID();
        markInFlight(runner, jobId);

        runner.runAsync(jobId, 2);

        verify(jobRepository, never()).findById(jobId);
    }

    @SuppressWarnings("unchecked")
    private void markInFlight(EvaluationBackgroundRunner runner, UUID jobId) throws Exception {
        Field field = EvaluationBackgroundRunner.class.getDeclaredField("inFlightJobIds");
        field.setAccessible(true);
        ((Set<UUID>) field.get(runner)).add(jobId);
    }
}
