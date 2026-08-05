package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * A node in the engineering graph — one real engineering artifact. {@code type} is a stable lowercase kind
 * (e.g. {@code organization}, {@code project}, {@code provider}, {@code model}, {@code agent}, {@code prompt},
 * {@code dataset}, {@code evaluation}). {@code entityId}/{@code projectId} let the UI deep-link back to the
 * artifact's existing page; they are null for synthetic or top-level nodes.
 */
@Schema(name = "GraphNode", description = "A real engineering artifact in the graph")
public record GraphNode(
        @Schema(description = "Stable node id, e.g. \"provider:<uuid>\"") String id,
        @Schema(description = "Artifact kind (lowercase)") String type,
        @Schema(description = "Human-readable label") String label,
        @Schema(description = "Optional secondary label (e.g. provider type)") String subtitle,
        @Schema(description = "The underlying entity id, when the node maps to one") UUID entityId,
        @Schema(description = "The project the entity belongs to, when applicable") UUID projectId
) {
}
