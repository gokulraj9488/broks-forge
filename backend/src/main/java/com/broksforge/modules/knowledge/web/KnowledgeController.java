package com.broksforge.modules.knowledge.web;

import com.broksforge.modules.knowledge.domain.KnowledgeNodeType;
import com.broksforge.modules.knowledge.service.KnowledgeGraphService;
import com.broksforge.modules.knowledge.web.dto.KnowledgeDtos.KnowledgeGraphResponse;
import com.broksforge.modules.knowledge.web.dto.KnowledgeDtos.KnowledgeNodeDetailResponse;
import com.broksforge.modules.knowledge.web.dto.KnowledgeDtos.KnowledgeNodeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read API for the Engineering Knowledge Graph (ADR 0013) — the platform's catalogue
 * of failure modes, regressions, recommendations and optimisations. Reference data,
 * so it is not tenant-scoped; any authenticated user may read it.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Knowledge graph", description = "The Engineering Knowledge Graph")
public class KnowledgeController {

    private final KnowledgeGraphService knowledgeGraphService;

    public KnowledgeController(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @GetMapping("/nodes")
    @Operation(summary = "List knowledge nodes", description = "Optionally filter by node type or category")
    public List<KnowledgeNodeResponse> list(@RequestParam(required = false) KnowledgeNodeType type,
                                            @RequestParam(required = false) String category) {
        return knowledgeGraphService.list(type, category);
    }

    @GetMapping("/nodes/{nodeKey}")
    @Operation(summary = "Get a knowledge node with its graph neighbours")
    public KnowledgeNodeDetailResponse get(@PathVariable String nodeKey) {
        return knowledgeGraphService.getByKey(nodeKey);
    }

    @GetMapping("/graph")
    @Operation(summary = "Get the full knowledge graph (nodes and edges)")
    public KnowledgeGraphResponse graph() {
        return knowledgeGraphService.graph();
    }
}
