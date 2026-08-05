package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A first-class engineering-knowledge object — an Observation, Claim, Decision, Evidence or Knowledge node —
 * derived deterministically from the live engineering model (evaluations, version promotions, status changes).
 * It is a real, traceable projection: nothing here is fabricated, and every object links back to the artifacts
 * and objects it was derived from via {@link KnowledgeLink}s.
 *
 * <p>The {@code id} is stable and composite (e.g. {@code "decision:prompt-version:<uuid>"}) so a single object
 * can be fetched and referenced across the graph, the registry and artifact views.
 */
@Schema(name = "KnowledgeObject", description = "A derived, traceable engineering-knowledge object")
public record KnowledgeObject(
        @Schema(description = "Stable composite id, e.g. \"decision:prompt-version:<uuid>\"") String id,
        @Schema(description = "observation | claim | decision | evidence | knowledge") String type,
        @Schema(description = "Short human title") String title,
        @Schema(description = "One or two sentence explanation, derived from real fields") String summary,
        @Schema(description = "The recorded rationale (release/version notes), when one exists") String rationale,
        @Schema(description = "The subject artifact's kind") String artifactType,
        @Schema(description = "The subject artifact's entity id") UUID artifactEntityId,
        @Schema(description = "Owning project id") UUID projectId,
        @Schema(description = "Outcome/status for observations and evidence (evaluation status), else null") String outcome,
        @Schema(description = "When the underlying engineering event happened") Instant at,
        @Schema(description = "Traceable links to artifacts and other knowledge objects") List<KnowledgeLink> links
) {
}
