package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Evidence bearing on an artifact — a real evaluation that referenced it, with its outcome/status. */
@Schema(name = "EvolutionEvidence", description = "A real evaluation that provides evidence about an artifact")
public record EvolutionEvidence(
        @Schema(description = "Graph node id, e.g. \"evaluation:<uuid>\"") String id,
        @Schema(description = "Artifact kind (always evaluation for now)") String type,
        @Schema(description = "Display name") String name,
        @Schema(description = "Outcome/status of the evaluation") String outcome,
        @Schema(description = "Underlying entity id") UUID entityId,
        @Schema(description = "Owning project id") UUID projectId,
        @Schema(description = "When the evidence was produced") Instant at
) {
}
