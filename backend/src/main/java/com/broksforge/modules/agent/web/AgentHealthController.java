package com.broksforge.modules.agent.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.agent.service.AgentHealthService;
import com.broksforge.modules.agent.web.dto.AgentHealthCheckResponse;
import com.broksforge.modules.agent.web.dto.AgentHealthSummaryResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Agent health: trigger manual checks and read current status, availability and
 * history. The manual-check path is the same one a future scheduler will use.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/agents/{agentId}")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Agent Health", description = "Run and review agent health checks")
public class AgentHealthController {

    private final AgentHealthService healthService;

    public AgentHealthController(AgentHealthService healthService) {
        this.healthService = healthService;
    }

    @PostMapping("/health-check")
    @Operation(summary = "Run a manual health check",
            description = "Probes the agent endpoint now and records the result.")
    public ResponseEntity<AgentHealthCheckResponse> runCheck(@PathVariable UUID organizationId,
                                                             @PathVariable UUID projectId,
                                                             @PathVariable UUID agentId) {
        return ResponseEntity.ok(healthService.runManualCheck(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId));
    }

    @GetMapping("/health")
    @Operation(summary = "Get health summary",
            description = "Current status, rolling availability % and the most recent checks.")
    public ResponseEntity<AgentHealthSummaryResponse> summary(@PathVariable UUID organizationId,
                                                              @PathVariable UUID projectId,
                                                              @PathVariable UUID agentId) {
        return ResponseEntity.ok(healthService.getSummary(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId));
    }

    @GetMapping("/health/history")
    @Operation(summary = "Get health-check history")
    public ResponseEntity<PageResponse<AgentHealthCheckResponse>> history(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @PathVariable UUID agentId,
            @ParameterObject @PageableDefault(size = 20, sort = "checkedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(healthService.getHistory(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, pageable));
    }
}
