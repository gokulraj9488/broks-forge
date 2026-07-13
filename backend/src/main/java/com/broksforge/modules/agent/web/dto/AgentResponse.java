package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.AgentAuthType;
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
 * Full representation of an agent. Never includes credential secrets.
 */
@Schema(name = "AgentResponse")
public record AgentResponse(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String name,
        String slug,
        String description,
        UUID ownerId,
        AgentVisibility visibility,
        AgentFramework framework,
        AgentLanguage language,
        String endpointUrl,
        AgentAuthType authType,
        UUID currentActiveVersionId,
        @Schema(description = "Linked Provider, if any (Provider abstraction milestone)") UUID providerId,
        String modelOverride,
        String endpointOverride,
        AgentHealthStatus healthStatus,
        Instant lastHealthCheckAt,
        AgentStatus status,
        AgentCapabilitiesDto capabilities,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt,

        /**
         * Whether an active authentication credential exists for this agent.
         * Non-sensitive readiness signal (no secret) so any member can tell an
         * agent is usable and drive onboarding/gating without credential access.
         * Always {@code true} for {@link AgentAuthType#NONE} agents (nothing to configure).
         */
        boolean credentialConfigured
) {
}
