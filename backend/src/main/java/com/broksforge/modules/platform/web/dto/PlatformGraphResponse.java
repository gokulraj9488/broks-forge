package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The engineering graph for one organization: real artifacts ({@link GraphNode}) connected by real
 * relationships ({@link GraphEdge}). Read-only and deterministic — a projection of the current domain state.
 */
@Schema(name = "PlatformGraphResponse", description = "The organization's engineering graph (nodes + edges)")
public record PlatformGraphResponse(
        List<GraphNode> nodes,
        List<GraphEdge> edges
) {
}
