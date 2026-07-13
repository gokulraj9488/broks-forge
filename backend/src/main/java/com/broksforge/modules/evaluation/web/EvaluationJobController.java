package com.broksforge.modules.evaluation.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.web.dto.CreateEvaluationJobRequest;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobFilter;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobSummaryResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationResultResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationRunResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Evaluation jobs: the top-level evaluation API. A job is created (pinning its
 * inputs), executed, then inspected via its runs and per-metric results.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/evaluation-jobs")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Evaluation jobs", description = "Create, run and inspect evaluation jobs")
public class EvaluationJobController {

    private final EvaluationService evaluationService;

    public EvaluationJobController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    @Operation(summary = "Create an evaluation job",
            description = "Pins the dataset/prompt versions. Set autoRun=true to execute immediately.")
    public ResponseEntity<EvaluationJobResponse> create(@PathVariable UUID organizationId,
                                                        @PathVariable UUID projectId,
                                                        @Valid @RequestBody CreateEvaluationJobRequest request) {
        EvaluationJobResponse response =
                evaluationService.create(SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List & search evaluation jobs")
    public ResponseEntity<PageResponse<EvaluationJobSummaryResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @ParameterObject EvaluationJobFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(evaluationService.search(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, filter, pageable));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get an evaluation job with its summary")
    public ResponseEntity<EvaluationJobResponse> get(@PathVariable UUID organizationId,
                                                     @PathVariable UUID projectId,
                                                     @PathVariable UUID jobId) {
        return ResponseEntity.ok(evaluationService.get(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, jobId));
    }

    @PostMapping("/{jobId}/run")
    @Operation(summary = "Run a pending evaluation job",
            description = "Executes the job synchronously: invokes the target for each dataset item and scores metrics.")
    public ResponseEntity<EvaluationJobResponse> run(@PathVariable UUID organizationId,
                                                     @PathVariable UUID projectId,
                                                     @PathVariable UUID jobId) {
        return ResponseEntity.ok(evaluationService.run(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, jobId));
    }

    @PostMapping("/{jobId}/resume")
    @Operation(summary = "Resume a failed/cancelled/stalled job",
            description = "Re-executes only the items that don't already have a succeeded run, in a new pass.")
    public ResponseEntity<EvaluationJobResponse> resume(@PathVariable UUID organizationId,
                                                        @PathVariable UUID projectId,
                                                        @PathVariable UUID jobId) {
        return ResponseEntity.ok(evaluationService.resume(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, jobId));
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "Cancel a pending or running job")
    public ResponseEntity<EvaluationJobResponse> cancel(@PathVariable UUID organizationId,
                                                        @PathVariable UUID projectId,
                                                        @PathVariable UUID jobId) {
        return ResponseEntity.ok(evaluationService.cancel(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, jobId));
    }

    @DeleteMapping("/{jobId}")
    @Operation(summary = "Delete an evaluation job", description = "Soft-deletes the job. Requires ADMIN or higher.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID jobId) {
        evaluationService.delete(SecurityUtils.requireCurrentUserId(), organizationId, projectId, jobId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{jobId}/runs")
    @Operation(summary = "List the runs of an evaluation job")
    public ResponseEntity<PageResponse<EvaluationRunResponse>> listRuns(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @ParameterObject @PageableDefault(size = 50, sort = "sequence", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(evaluationService.listRuns(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, jobId, pageable));
    }

    @GetMapping("/{jobId}/runs/{runId}/results")
    @Operation(summary = "List the metric results for a run")
    public ResponseEntity<List<EvaluationResultResponse>> listResults(@PathVariable UUID organizationId,
                                                                      @PathVariable UUID projectId,
                                                                      @PathVariable UUID jobId,
                                                                      @PathVariable UUID runId) {
        return ResponseEntity.ok(evaluationService.listResults(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, jobId, runId));
    }
}
