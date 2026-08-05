package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One real historical revision of an artifact (a version record) — how the artifact changed over time. */
@Schema(name = "EvolutionRevision", description = "A real historical revision of an artifact")
public record EvolutionRevision(
        @Schema(description = "Short label, e.g. \"v3\"") String label,
        @Schema(description = "Detail, e.g. release notes") String detail,
        @Schema(description = "Whether this is the currently active revision") boolean active,
        @Schema(description = "When the revision was created") Instant at
) {
}
