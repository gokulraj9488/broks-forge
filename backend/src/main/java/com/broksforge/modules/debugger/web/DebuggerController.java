package com.broksforge.modules.debugger.web;

import com.broksforge.modules.debugger.service.DebuggerService;
import com.broksforge.modules.debugger.web.dto.DebuggerDtos.ExecutionTimelineResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The AI Debugger (Phase 4, ADR 0014): a stage-by-stage execution timeline for a single
 * evaluation run, so a failed result can be explained, not just observed.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/debugger")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Debugger", description = "Execution timeline for a single evaluation run")
public class DebuggerController {

    private final DebuggerService debuggerService;

    public DebuggerController(DebuggerService debuggerService) {
        this.debuggerService = debuggerService;
    }

    @GetMapping("/jobs/{jobId}/runs/{runId}/timeline")
    @Operation(summary = "Reconstruct the execution timeline for a run")
    public ExecutionTimelineResponse timeline(@PathVariable UUID organizationId,
                                              @PathVariable UUID projectId,
                                              @PathVariable UUID jobId,
                                              @PathVariable UUID runId) {
        return debuggerService.timeline(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, jobId, runId);
    }
}
