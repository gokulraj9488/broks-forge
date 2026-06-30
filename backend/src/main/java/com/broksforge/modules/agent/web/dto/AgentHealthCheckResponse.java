package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.AgentHealthStatus;
import com.broksforge.modules.agent.domain.HealthCheckType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "AgentHealthCheckResponse")
public record AgentHealthCheckResponse(
        UUID id,
        UUID agentId,
        UUID versionId,
        HealthCheckType checkType,
        AgentHealthStatus status,
        boolean success,
        Integer httpStatus,
        Long latencyMs,
        Instant checkedAt,
        String failureReason
) {
}
