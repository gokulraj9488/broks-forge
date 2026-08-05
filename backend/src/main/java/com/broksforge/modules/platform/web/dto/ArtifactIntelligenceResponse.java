package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The engineering intelligence of a single artifact — everything the platform can reason about it, derived
 * from the live model: what was <em>observed</em>, what is <em>claimed</em>, what was <em>decided</em>, what
 * <em>evidence</em> supports it, the durable <em>knowledge</em> that emerged, and the engineering
 * <em>memory</em> (the "why"). Nothing is fabricated; every element is traceable to a real artifact or event.
 */
@Schema(name = "ArtifactIntelligenceResponse", description = "The engineering intelligence of one artifact")
public record ArtifactIntelligenceResponse(
        @Schema(description = "The subject artifact") EvolutionRef artifact,
        @Schema(description = "Measured facts from evaluations") List<KnowledgeObject> observations,
        @Schema(description = "Assertions supported by evidence") List<KnowledgeObject> claims,
        @Schema(description = "Engineering decisions (promotions, deprecations)") List<KnowledgeObject> decisions,
        @Schema(description = "Evaluations that provide supporting evidence") List<KnowledgeObject> evidence,
        @Schema(description = "Durable knowledge that emerged from decisions + evidence") List<KnowledgeObject> knowledge,
        @Schema(description = "The 'why' narrative, derived from the decisions") List<MemoryEntry> memory
) {
}
