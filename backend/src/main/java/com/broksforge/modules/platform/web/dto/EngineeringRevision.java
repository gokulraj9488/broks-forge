package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One engineering revision of an artifact — a real, immutable version record framed for "AI Git": what the
 * revision is ({@code label}), what it holds ({@code snapshot}), why it exists ({@code rationale}), whether it
 * is the promoted/active revision, and whether it can be rolled back to. The {@code snapshot} is a stable,
 * ordered field→value map so two revisions can be compared field by field.
 */
@Schema(name = "EngineeringRevision", description = "A real engineering revision (version) of an artifact")
public record EngineeringRevision(
        @Schema(description = "Stable revision id, e.g. \"prompt-version:<uuid>\"") String id,
        @Schema(description = "Subject artifact kind") String artifactType,
        @Schema(description = "Subject artifact entity id") UUID artifactEntityId,
        @Schema(description = "Display label, e.g. \"v3\" or \"1.2.0\"") String label,
        @Schema(description = "Short one-line description of this revision") String detail,
        @Schema(description = "Recorded rationale (notes / release notes)") String rationale,
        @Schema(description = "Whether this is the currently promoted/active revision") boolean active,
        @Schema(description = "Whether the platform can roll back to this revision") boolean rollbackReady,
        @Schema(description = "When this revision was created") Instant at,
        @Schema(description = "Ordered field→value snapshot, for comparison") Map<String, String> snapshot
) {
}
