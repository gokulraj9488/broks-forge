package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The engineering evolution of a single artifact — where it came from ({@code dependencies}), what it
 * influences ({@code dependents}/{@code impactCount}), how it changed ({@code history}) and what evidence
 * bears on it ({@code evidence}). Entirely derived from the live engineering model; nothing is fabricated.
 */
@Schema(name = "ArtifactEvolutionResponse", description = "The engineering evolution of one artifact")
public record ArtifactEvolutionResponse(
        EvolutionRef artifact,
        @Schema(description = "What this artifact directly depends on (upstream lineage)") List<EvolutionRef> dependencies,
        @Schema(description = "What directly depends on this artifact (downstream)") List<EvolutionRef> dependents,
        @Schema(description = "Total downstream artifacts transitively affected by a change here") int impactCount,
        @Schema(description = "Real historical revisions, newest first") List<EvolutionRevision> history,
        @Schema(description = "Evaluations that provide evidence about this artifact") List<EvolutionEvidence> evidence
) {
}
