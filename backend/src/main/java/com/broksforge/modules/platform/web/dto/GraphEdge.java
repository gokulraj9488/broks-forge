package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A directed relationship between two {@link GraphNode}s — one real engineering relationship. {@code relation}
 * is a stable lowercase verb (e.g. {@code contains}, {@code uses}, {@code provides}, {@code evaluates}).
 */
@Schema(name = "GraphEdge", description = "A real engineering relationship between two artifacts")
public record GraphEdge(
        @Schema(description = "Stable edge id") String id,
        @Schema(description = "Source node id") String source,
        @Schema(description = "Target node id") String target,
        @Schema(description = "Relationship kind (lowercase)") String relation
) {
}
