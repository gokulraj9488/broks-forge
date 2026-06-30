package com.broksforge.modules.dashboard.web;

import com.broksforge.modules.dashboard.service.DashboardService;
import com.broksforge.modules.dashboard.web.dto.DashboardDtos.DashboardResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The project dashboard: a single read-only roll-up of agents, evaluation activity,
 * analytics, reports and regression alerts.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/dashboard")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Project dashboard roll-up")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Get the project dashboard")
    public ResponseEntity<DashboardResponse> overview(@PathVariable UUID organizationId,
                                                      @PathVariable UUID projectId) {
        return ResponseEntity.ok(dashboardService.overview(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId));
    }
}
