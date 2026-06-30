package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.AgentFramework;
import com.broksforge.modules.agent.domain.AgentHealthStatus;
import com.broksforge.modules.agent.domain.AgentLanguage;
import com.broksforge.modules.agent.domain.AgentStatus;
import com.broksforge.modules.agent.domain.AgentVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight agent representation for list/search results.
 */
@Schema(name = "AgentSummaryResponse")
public record AgentSummaryResponse(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String name,
        String slug,
        String description,
        AgentFramework framework,
        AgentLanguage language,
        AgentVisibility visibility,
        AgentStatus status,
        AgentHealthStatus healthStatus,
        Instant lastHealthCheckAt,
        UUID currentActiveVersionId,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {
}
