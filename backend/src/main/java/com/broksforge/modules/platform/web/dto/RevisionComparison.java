package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * A field-by-field comparison of two engineering revisions of the same artifact — "what changed" between them.
 * Derived by diffing the two immutable version snapshots; nothing is stored or invented.
 */
@Schema(name = "RevisionComparison", description = "A comparison of two engineering revisions")
public record RevisionComparison(
        @Schema(description = "Subject artifact kind") String artifactType,
        @Schema(description = "Subject artifact entity id") UUID artifactEntityId,
        @Schema(description = "The base (older) revision") EngineeringRevision base,
        @Schema(description = "The target (newer) revision") EngineeringRevision target,
        @Schema(description = "Field-level differences") List<RevisionDiff> diffs
) {
}
