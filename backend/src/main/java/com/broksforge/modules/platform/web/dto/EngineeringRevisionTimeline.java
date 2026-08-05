package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The engineering revision history ("AI Git" timeline) of one artifact — its real revisions newest-first, and
 * how many of them were ever promoted. Derived entirely from the artifact's immutable version records.
 */
@Schema(name = "EngineeringRevisionTimeline", description = "An artifact's engineering revision timeline")
public record EngineeringRevisionTimeline(
        @Schema(description = "The subject artifact") EvolutionRef artifact,
        @Schema(description = "Real revisions, newest first") List<EngineeringRevision> revisions,
        @Schema(description = "How many revisions are/were promoted") int promotions
) {
}
