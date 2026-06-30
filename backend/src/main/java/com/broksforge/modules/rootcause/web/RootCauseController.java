package com.broksforge.modules.rootcause.web;

import com.broksforge.modules.rootcause.service.RootCauseService;
import com.broksforge.modules.rootcause.web.dto.RootCauseDtos.RootCauseReportResponse;
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
 * Root-cause analysis (Phase 4, ADR 0012): explains why an evaluation job failed or a
 * regression occurred, as actionable findings. Read-only and computed on demand.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/root-cause")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Root-cause analysis", description = "Diagnose why evaluations fail or regress")
public class RootCauseController {

    private final RootCauseService rootCauseService;

    public RootCauseController(RootCauseService rootCauseService) {
        this.rootCauseService = rootCauseService;
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Analyse an evaluation job's failures")
    public RootCauseReportResponse analyzeJob(@PathVariable UUID organizationId,
                                              @PathVariable UUID projectId,
                                              @PathVariable UUID jobId) {
        return rootCauseService.analyzeJob(SecurityUtils.requireCurrentUserId(), organizationId, projectId, jobId);
    }

    @GetMapping("/regressions/{checkId}")
    @Operation(summary = "Analyse a regression check")
    public RootCauseReportResponse analyzeRegression(@PathVariable UUID organizationId,
                                                     @PathVariable UUID projectId,
                                                     @PathVariable UUID checkId) {
        return rootCauseService.analyzeRegression(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, checkId);
    }
}
