package com.broksforge.modules.provider.web.dto;

import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.agent.web.dto.AgentCapabilitiesDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

@Schema(name = "CreateProviderRequest")
public record CreateProviderRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull LlmProvider type,
        @NotBlank @Size(max = 2048) String baseUrl,
        AgentAuthType authType,
        @Schema(description = "Write-only; encrypted at rest, never returned") String apiKey,
        Map<String, Object> defaultHeaders,
        @Size(max = 128) String defaultModel,
        List<String> supportedModels,
        AgentCapabilitiesDto capabilities,
        Map<String, Object> rateLimits,
        Map<String, Object> pricingMetadata
) {
}
