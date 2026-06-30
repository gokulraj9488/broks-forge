package com.broksforge.modules.analytics.web;

import com.broksforge.modules.analytics.service.AnalyticsService;
import com.broksforge.modules.analytics.web.dto.AnalyticsDtos.AnalyticsOverviewResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Cost, latency, token and usage analytics with a daily trend.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/analytics")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Analytics", description = "Evaluation cost, latency, token and usage analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    @Operation(summary = "Analytics overview", description = "Aggregates and a daily trend over the given window.")
    public ResponseEntity<AnalyticsOverviewResponse> overview(@PathVariable UUID organizationId,
                                                              @PathVariable UUID projectId,
                                                              @RequestParam(defaultValue = "30") int windowDays) {
        return ResponseEntity.ok(analyticsService.overview(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, windowDays));
    }
}
