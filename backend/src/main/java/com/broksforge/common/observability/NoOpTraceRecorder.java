package com.broksforge.common.observability;

import org.springframework.stereotype.Component;

/**
 * Default {@link TraceRecorder} that discards spans. It satisfies the recording seam
 * so business code can be instrumented today, while honestly reporting that no
 * exporter is active. Replaced by a real exporter-backed recorder when distributed
 * tracing is wired (ADR 0010).
 */
@Component
public class NoOpTraceRecorder implements TraceRecorder {

    @Override
    public void recordStage(String correlationId, ExecutionStage stage, StageStatus status,
                            long durationMs, String detail) {
        // Intentionally no-op: the tracing exporter is deliberately out of Phase 4 scope.
    }

    @Override
    public boolean isActive() {
        return false;
    }
}
