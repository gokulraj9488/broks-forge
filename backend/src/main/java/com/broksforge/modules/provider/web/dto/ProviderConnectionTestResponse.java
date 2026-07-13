package com.broksforge.modules.provider.web.dto;

import com.broksforge.modules.agent.domain.HealthProbeStrategy;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProviderConnectionTestResponse", description = "Result of a one-shot reachability/credential test")
public record ProviderConnectionTestResponse(
        boolean success,
        Integer httpStatus,
        long latencyMs,
        String message,
        @Schema(description = "How the probe was performed (provider-aware)")
        HealthProbeStrategy probeStrategy,
        @Schema(description = "The exact URL that was probed")
        String probeUrl
) {
}
