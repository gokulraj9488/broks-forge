package com.broksforge.modules.provider.web.dto;

import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.domain.AgentHealthStatus;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.agent.web.dto.AgentCapabilitiesDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(name = "ProviderResponse", description = "A registered LLM provider configuration (never includes the API key)")
public record ProviderResponse(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String name,
        LlmProvider type,
        String baseUrl,
        AgentAuthType authType,
        @Schema(description = "Last 4 characters of the configured key, or null if none is set") String apiKeyHint,
        boolean apiKeyConfigured,
        Map<String, Object> defaultHeaders,
        String defaultModel,
        List<String> supportedModels,
        AgentCapabilitiesDto capabilities,
        Map<String, Object> rateLimits,
        Map<String, Object> pricingMetadata,
        AgentHealthStatus healthStatus,
        Instant lastHealthCheckAt,
        boolean enabled,
        @Schema(description = "Timestamp of the most recent invocation through an agent linked to this "
                + "provider, or null if it has never been used") Instant lastUsedAt,
        @Schema(description = "Number of models in supportedModels; 0 if none are declared") int modelCount,
        int linkedAgentCount,
        Instant createdAt,
        Instant updatedAt
) {
}
