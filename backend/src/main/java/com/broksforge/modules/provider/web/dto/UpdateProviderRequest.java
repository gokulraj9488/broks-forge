package com.broksforge.modules.provider.web.dto;

import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.web.dto.AgentCapabilitiesDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** All fields optional — only supplied ones are changed. A blank {@code apiKey} keeps the stored one. */
@Schema(name = "UpdateProviderRequest")
public record UpdateProviderRequest(
        @Size(max = 120) String name,
        @Size(max = 2048) String baseUrl,
        AgentAuthType authType,
        String apiKey,
        Map<String, Object> defaultHeaders,
        @Size(max = 128) String defaultModel,
        List<String> supportedModels,
        AgentCapabilitiesDto capabilities,
        Map<String, Object> rateLimits,
        Map<String, Object> pricingMetadata
) {
}
