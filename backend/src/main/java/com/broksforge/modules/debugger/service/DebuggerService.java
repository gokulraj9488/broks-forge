package com.broksforge.modules.debugger.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.observability.ExecutionStage;
import com.broksforge.common.observability.StageStatus;
import com.broksforge.common.observability.TraceRecorder;
import com.broksforge.modules.debugger.web.dto.DebuggerDtos.ExecutionTimelineResponse;
import com.broksforge.modules.debugger.web.dto.DebuggerDtos.TimelineStageResponse;
import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.EvaluationRunStatus;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationResultResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationRunResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The AI Debugger (ADR 0014): reconstructs a stage-by-stage execution timeline for a
 * single evaluation run so a failed result is explainable, not just red.
 *
 * <p>Today the timeline is reconstructed from persisted run data (input, output,
 * latency, tokens, cost, HTTP status, metric results). The stages the platform does
 * not yet instrument — memory, retrieval and tool calls — are reported honestly as
 * {@link StageStatus#NOT_INSTRUMENTED} rather than faked. When the {@link TraceRecorder}
 * seam is driven by live instrumentation (Phase 5/6), those stages light up without
 * any change to this contract.</p>
 */
@Service
public class DebuggerService {

    private static final int PREVIEW = 280;

    private final EvaluationService evaluationService;
    private final TraceRecorder traceRecorder;

    public DebuggerService(EvaluationService evaluationService, TraceRecorder traceRecorder) {
        this.evaluationService = evaluationService;
        this.traceRecorder = traceRecorder;
    }

    @Transactional(readOnly = true)
    public ExecutionTimelineResponse timeline(UUID actorId, UUID organizationId, UUID projectId,
                                              UUID jobId, UUID runId) {
        EvaluationJobResponse job = evaluationService.get(actorId, organizationId, projectId, jobId);
        EvaluationRunResponse run = evaluationService.getRun(actorId, organizationId, projectId, jobId, runId);
        List<EvaluationResultResponse> results =
                evaluationService.listResults(actorId, organizationId, projectId, jobId, runId);

        if (run.status() == EvaluationRunStatus.PENDING || run.status() == EvaluationRunStatus.RUNNING) {
            throw new ResourceConflictException(ErrorCode.DEBUG_TIMELINE_UNAVAILABLE,
                    "This run has not finished executing yet; no timeline is available.");
        }

        boolean runFailed = run.status() == EvaluationRunStatus.FAILED;
        long latency = run.latencyMs() == null ? 0L : run.latencyMs();
        long offset = 0L;

        List<TimelineStageResponse> stages = new ArrayList<>();

        // PROMPT — we record the item input; the fully rendered prompt is not persisted per run.
        String promptDetail = "Input: " + (StringUtils.hasText(run.input())
                ? run.input().length() + " chars — \"" + preview(run.input()) + "\""
                : "none")
                + (job.promptId() != null ? " · prompt template applied" : "");
        stages.add(new TimelineStageResponse(ExecutionStage.PROMPT, ExecutionStage.PROMPT.label(),
                StringUtils.hasText(run.input()) ? StageStatus.OK : StageStatus.WARN, offset, 0L, promptDetail, null));

        // MEMORY / RETRIEVER / TOOLS — not yet instrumented (honest placeholders).
        stages.add(notInstrumented(ExecutionStage.MEMORY,
                "Memory/state access is not yet captured by the platform."));
        stages.add(notInstrumented(ExecutionStage.RETRIEVER,
                "Retrieval is not yet captured; the Phase 5 RAG inspector will populate this stage."));
        stages.add(notInstrumented(ExecutionStage.TOOLS,
                "Tool/function calls are not yet captured by the platform."));

        // MODEL — the one stage we time directly (the agent endpoint invocation).
        StageStatus modelStatus;
        String modelExplanation = null;
        if (runFailed || (run.httpStatus() != null && run.httpStatus() >= 400)) {
            modelStatus = StageStatus.ERROR;
            modelExplanation = StringUtils.hasText(run.error())
                    ? run.error()
                    : (run.httpStatus() != null ? "Endpoint returned HTTP " + run.httpStatus() : "Invocation failed");
        } else {
            modelStatus = StageStatus.OK;
        }
        String modelDetail = "%s%s · %s · %s tokens%s".formatted(
                job.provider() == null ? "" : job.provider().name() + " ",
                job.model() == null ? "(agent endpoint)" : job.model(),
                latency > 0 ? latency + "ms" : "latency n/a",
                run.totalTokens() == null ? "?" : run.totalTokens(),
                run.httpStatus() == null ? "" : " · HTTP " + run.httpStatus());
        stages.add(new TimelineStageResponse(ExecutionStage.MODEL, ExecutionStage.MODEL.label(),
                modelStatus, offset, latency, modelDetail, modelExplanation));
        offset += latency;

        // PARSER — driven by output presence and the JSON_VALID metric if configured.
        EvaluationResultResponse jsonResult = results.stream()
                .filter(r -> r.metricType() == EvaluationMetricType.JSON_VALID)
                .findFirst().orElse(null);
        StageStatus parserStatus;
        String parserExplanation = null;
        if (!StringUtils.hasText(run.output())) {
            parserStatus = StageStatus.ERROR;
            parserExplanation = "No output was produced to parse.";
        } else if (jsonResult != null && !jsonResult.passed()) {
            parserStatus = StageStatus.ERROR;
            parserExplanation = "Output failed JSON validation.";
        } else {
            parserStatus = StageStatus.OK;
        }
        stages.add(new TimelineStageResponse(ExecutionStage.PARSER, ExecutionStage.PARSER.label(),
                parserStatus, offset, 0L,
                jsonResult == null ? "No structural metric configured" : "JSON_VALID: "
                        + (jsonResult.passed() ? "passed" : "failed"), parserExplanation));

        // OUTPUT — final result and the failing metrics.
        long failedMetrics = results.stream().filter(r -> !r.passed()).count();
        StageStatus outputStatus = Boolean.TRUE.equals(run.passed()) ? StageStatus.OK
                : (runFailed ? StageStatus.ERROR : StageStatus.WARN);
        String outputDetail = (StringUtils.hasText(run.output())
                ? "Output: " + run.output().length() + " chars — \"" + preview(run.output()) + "\""
                : "Output: none")
                + " · %d/%d metrics failed".formatted(failedMetrics, results.size());
        stages.add(new TimelineStageResponse(ExecutionStage.OUTPUT, ExecutionStage.OUTPUT.label(),
                outputStatus, offset, 0L, outputDetail, null));

        String failureExplanation = explainFailure(run, runFailed, results);
        List<String> notes = new ArrayList<>();
        notes.add("Durations are best-effort: only the model invocation is timed end-to-end today.");
        notes.add("MEMORY, RETRIEVER and TOOLS stages are placeholders until live tracing is wired (Phase 5/6).");
        if (!traceRecorder.isActive()) {
            notes.add("Distributed tracing exporter is not active; timeline is reconstructed from persisted run data.");
        }

        return new ExecutionTimelineResponse(jobId, runId, run.sequence(), run.status(), run.passed(),
                run.latencyMs(), job.provider(), job.model(), run.promptTokens(), run.completionTokens(),
                run.totalTokens(), run.cost(), stages, failureExplanation, traceRecorder.isActive(), notes);
    }

    private TimelineStageResponse notInstrumented(ExecutionStage stage, String detail) {
        return new TimelineStageResponse(stage, stage.label(), StageStatus.NOT_INSTRUMENTED, null, null, detail, null);
    }

    private String explainFailure(EvaluationRunResponse run, boolean runFailed, List<EvaluationResultResponse> results) {
        if (runFailed) {
            return StringUtils.hasText(run.error())
                    ? "Run failed during invocation: " + run.error()
                    : "Run failed during invocation.";
        }
        List<String> failing = results.stream()
                .filter(r -> !r.passed())
                .map(r -> r.metricType().name() + (StringUtils.hasText(r.detail()) ? " (" + r.detail() + ")" : ""))
                .toList();
        if (failing.isEmpty()) {
            return null;
        }
        return "Failed metrics: " + String.join("; ", failing);
    }

    private String preview(String value) {
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= PREVIEW ? collapsed : collapsed.substring(0, PREVIEW) + "…";
    }
}
