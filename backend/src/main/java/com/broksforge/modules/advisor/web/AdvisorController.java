package com.broksforge.modules.advisor.web;

import com.broksforge.modules.advisor.service.AdvisorService;
import com.broksforge.modules.advisor.web.dto.AdvisorDtos.AdvisoryReportResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The AI Engineering Advisor (Phase 4, ADR 0011): turns evaluation, benchmark, agent
 * and prompt data into ranked, actionable engineering recommendations. Read-only:
 * recommendations are computed on demand from current platform state.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/advisor")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Engineering Advisor", description = "Actionable engineering recommendations")
public class AdvisorController {

    private final AdvisorService advisorService;

    public AdvisorController(AdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @GetMapping
    @Operation(summary = "Project advisory", description = "Model and cost recommendations across recent jobs")
    public AdvisoryReportResponse adviseProject(@PathVariable UUID organizationId,
                                                @PathVariable UUID projectId) {
        return advisorService.adviseProject(SecurityUtils.requireCurrentUserId(), organizationId, projectId);
    }

    @GetMapping("/agents/{agentId}")
    @Operation(summary = "Agent advisory", description = "Reliability, RAG and model recommendations for one agent")
    public AdvisoryReportResponse adviseAgent(@PathVariable UUID organizationId,
                                              @PathVariable UUID projectId,
                                              @PathVariable UUID agentId) {
        return advisorService.adviseAgent(SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId);
    }

    @GetMapping("/prompts/{promptId}")
    @Operation(summary = "Prompt advisory", description = "Static analysis of a prompt version")
    public AdvisoryReportResponse advisePrompt(@PathVariable UUID organizationId,
                                               @PathVariable UUID projectId,
                                               @PathVariable UUID promptId,
                                               @RequestParam(required = false) UUID versionId) {
        return advisorService.advisePrompt(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId, versionId);
    }
}
