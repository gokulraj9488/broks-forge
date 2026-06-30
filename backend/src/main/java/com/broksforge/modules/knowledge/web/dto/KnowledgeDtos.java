package com.broksforge.modules.knowledge.web.dto;

import com.broksforge.modules.knowledge.domain.KnowledgeNodeType;
import com.broksforge.modules.knowledge.domain.KnowledgeRelation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Read DTOs for the Engineering Knowledge Graph. The catalogue is reference data, so
 * these are response-only.
 */
public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    @Schema(name = "KnowledgeNodeResponse", description = "A node in the Engineering Knowledge Graph")
    public record KnowledgeNodeResponse(
            String nodeKey,
            KnowledgeNodeType nodeType,
            String title,
            String category,
            String summary,
            String detectionHint,
            String remediation,
            String expectedImprovement,
            String defaultSeverity,
            String defaultConfidence,
            List<String> tags,
            long occurrenceCount
    ) {
    }

    @Schema(name = "KnowledgeEdgeResponse", description = "A directed relationship between two knowledge nodes")
    public record KnowledgeEdgeResponse(
            String sourceNodeKey,
            String targetNodeKey,
            KnowledgeRelation relation,
            int weight
    ) {
    }

    @Schema(name = "KnowledgeGraphResponse", description = "The full knowledge graph: nodes and edges")
    public record KnowledgeGraphResponse(
            List<KnowledgeNodeResponse> nodes,
            List<KnowledgeEdgeResponse> edges
    ) {
    }

    @Schema(name = "KnowledgeNeighborResponse", description = "A neighbouring node reached via one edge")
    public record KnowledgeNeighborResponse(
            KnowledgeRelation relation,
            String direction,
            KnowledgeNodeResponse node
    ) {
    }

    @Schema(name = "KnowledgeNodeDetailResponse", description = "A knowledge node with its graph neighbours")
    public record KnowledgeNodeDetailResponse(
            KnowledgeNodeResponse node,
            List<KnowledgeNeighborResponse> neighbors
    ) {
    }
}
