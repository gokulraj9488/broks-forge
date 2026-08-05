package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** A related artifact in an evolution view — a real dependency or dependent, with the relationship verb. */
@Schema(name = "EvolutionRef", description = "A related engineering artifact (dependency or dependent)")
public record EvolutionRef(
        @Schema(description = "Graph node id, e.g. \"provider:<uuid>\"") String id,
        @Schema(description = "Artifact kind") String type,
        @Schema(description = "Display name") String name,
        @Schema(description = "Underlying entity id") UUID entityId,
        @Schema(description = "Owning project id") UUID projectId,
        @Schema(description = "Relationship verb (uses/evaluates/provides/…)") String relation
) {
}
