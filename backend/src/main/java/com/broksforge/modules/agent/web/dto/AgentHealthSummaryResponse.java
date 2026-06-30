package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.AgentHealthStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated health view for an agent: current status plus availability computed
 * over a rolling window and the most recent checks.
 */
@Schema(name = "AgentHealthSummaryResponse")
public record AgentHealthSummaryResponse(
        UUID agentId,
        AgentHealthStatus currentStatus,
        Instant lastCheckedAt,
        @Schema(description = "Availability percentage over the window (0-100), or null if no checks")
        Double availabilityPercent,
        int windowDays,
        long totalChecks,
        long successfulChecks,
        List<AgentHealthCheckResponse> recent
) {
}
