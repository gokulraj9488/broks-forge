package com.broksforge.modules.agent.web.dto;

import com.broksforge.common.validation.ValidEndpointUrl;
import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.domain.AgentFramework;
import com.broksforge.modules.agent.domain.AgentLanguage;
import com.broksforge.modules.agent.domain.AgentVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Partial update. Null fields are left unchanged. The slug and owner are
 * immutable here; lifecycle (archive) is changed through dedicated endpoints.
 */
@Schema(name = "UpdateAgentRequest")
public record UpdateAgentRequest(

        @Size(min = 2, max = 120)
        String name,

        @Size(max = 1000)
        String description,

        AgentVisibility visibility,

        AgentFramework framework,

        AgentLanguage language,

        @ValidEndpointUrl
        String endpointUrl,

        AgentAuthType authType,

        @Valid
        AgentCapabilitiesDto capabilities,

        @Schema(description = "When provided, replaces the agent's tags wholesale")
        @Size(max = 25, message = "An agent may have at most 25 tags")
        Set<@Size(max = 64) String> tags,

        @Schema(description = "When provided, links (or re-links) this agent to a Provider")
        UUID providerId,

        @Size(max = 128)
        String modelOverride,

        @ValidEndpointUrl
        String endpointOverride
) {
}
