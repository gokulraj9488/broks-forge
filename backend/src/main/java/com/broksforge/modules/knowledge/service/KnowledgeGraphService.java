package com.broksforge.modules.knowledge.service;

import com.broksforge.common.exception.ApiException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.modules.knowledge.domain.KnowledgeEdge;
import com.broksforge.modules.knowledge.domain.KnowledgeNode;
import com.broksforge.modules.knowledge.domain.KnowledgeNodeType;
import com.broksforge.modules.knowledge.repository.KnowledgeEdgeRepository;
import com.broksforge.modules.knowledge.repository.KnowledgeNodeRepository;
import com.broksforge.modules.knowledge.web.dto.KnowledgeDtos.KnowledgeEdgeResponse;
import com.broksforge.modules.knowledge.web.dto.KnowledgeDtos.KnowledgeGraphResponse;
import com.broksforge.modules.knowledge.web.dto.KnowledgeDtos.KnowledgeNeighborResponse;
import com.broksforge.modules.knowledge.web.dto.KnowledgeDtos.KnowledgeNodeDetailResponse;
import com.broksforge.modules.knowledge.web.dto.KnowledgeDtos.KnowledgeNodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read access to the Engineering Knowledge Graph (ADR 0013) plus the two seams the
 * rest of the platform builds on:
 * <ul>
 *     <li>{@link #findPattern(String)} — lets the advisor and root-cause engines link a
 *         finding to canonical knowledge by stable key.</li>
 *     <li>{@link #recordObservation(String)} — increments a pattern's occurrence count,
 *         the foundation for future learning from real platform usage.</li>
 * </ul>
 *
 * <p>The graph is platform-global reference data; reads only require an authenticated
 * caller (enforced at the controller). No tenant scoping applies.</p>
 */
@Slf4j
@Service
public class KnowledgeGraphService {

    private final KnowledgeNodeRepository nodeRepository;
    private final KnowledgeEdgeRepository edgeRepository;

    public KnowledgeGraphService(KnowledgeNodeRepository nodeRepository,
                                 KnowledgeEdgeRepository edgeRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeNodeResponse> list(KnowledgeNodeType type, String category) {
        List<KnowledgeNode> nodes;
        if (type != null) {
            nodes = nodeRepository.findByNodeTypeOrderByTitleAsc(type);
        } else if (StringUtils.hasText(category)) {
            nodes = nodeRepository.findByCategoryIgnoreCaseOrderByTitleAsc(category.trim());
        } else {
            nodes = nodeRepository.findAllByOrderByCategoryAscTitleAsc();
        }
        return nodes.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeNodeDetailResponse getByKey(String nodeKey) {
        KnowledgeNode node = nodeRepository.findByNodeKey(nodeKey)
                .orElseThrow(() -> new ApiException(ErrorCode.KNOWLEDGE_PATTERN_NOT_FOUND,
                        "Knowledge node '" + nodeKey + "' not found"));

        Map<UUID, KnowledgeNode> byId = nodeRepository.findAll().stream()
                .collect(Collectors.toMap(KnowledgeNode::getId, Function.identity()));

        List<KnowledgeNeighborResponse> neighbors = new ArrayList<>();
        for (KnowledgeEdge edge : edgeRepository.findBySourceNodeId(node.getId())) {
            KnowledgeNode other = byId.get(edge.getTargetNodeId());
            if (other != null) {
                neighbors.add(new KnowledgeNeighborResponse(edge.getRelation(), "OUTGOING", toResponse(other)));
            }
        }
        for (KnowledgeEdge edge : edgeRepository.findByTargetNodeId(node.getId())) {
            KnowledgeNode other = byId.get(edge.getSourceNodeId());
            if (other != null) {
                neighbors.add(new KnowledgeNeighborResponse(edge.getRelation(), "INCOMING", toResponse(other)));
            }
        }
        return new KnowledgeNodeDetailResponse(toResponse(node), neighbors);
    }

    @Transactional(readOnly = true)
    public KnowledgeGraphResponse graph() {
        Map<UUID, KnowledgeNode> byId = nodeRepository.findAllByOrderByCategoryAscTitleAsc().stream()
                .collect(Collectors.toMap(KnowledgeNode::getId, Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));

        List<KnowledgeNodeResponse> nodes = byId.values().stream().map(this::toResponse).toList();
        List<KnowledgeEdgeResponse> edges = edgeRepository.findAll().stream()
                .map(edge -> {
                    KnowledgeNode source = byId.get(edge.getSourceNodeId());
                    KnowledgeNode target = byId.get(edge.getTargetNodeId());
                    if (source == null || target == null) {
                        return null;
                    }
                    return new KnowledgeEdgeResponse(source.getNodeKey(), target.getNodeKey(),
                            edge.getRelation(), edge.getWeight());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return new KnowledgeGraphResponse(nodes, edges);
    }

    // ----------------------------------------------------------------------
    // Published seams for the advisor / root-cause engines
    // ----------------------------------------------------------------------

    /** Resolves canonical knowledge for a stable pattern key, if present. Never throws. */
    @Transactional(readOnly = true)
    public Optional<KnowledgePattern> findPattern(String nodeKey) {
        if (!StringUtils.hasText(nodeKey)) {
            return Optional.empty();
        }
        return nodeRepository.findByNodeKey(nodeKey).map(node -> new KnowledgePattern(
                node.getNodeKey(), node.getTitle(), node.getCategory(), node.getRemediation(),
                node.getExpectedImprovement(), node.getDefaultSeverity(), node.getDefaultConfidence()));
    }

    /**
     * Records that the platform observed this pattern — the learning seam. Best-effort:
     * an unknown key is ignored, and the increment never propagates failure to callers.
     */
    @Transactional
    public void recordObservation(String nodeKey) {
        if (!StringUtils.hasText(nodeKey)) {
            return;
        }
        try {
            nodeRepository.incrementOccurrence(nodeKey);
        } catch (RuntimeException e) {
            log.debug("Could not record knowledge observation for '{}': {}", nodeKey, e.getMessage());
        }
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private KnowledgeNodeResponse toResponse(KnowledgeNode node) {
        return new KnowledgeNodeResponse(
                node.getNodeKey(), node.getNodeType(), node.getTitle(), node.getCategory(), node.getSummary(),
                node.getDetectionHint(), node.getRemediation(), node.getExpectedImprovement(),
                node.getDefaultSeverity(), node.getDefaultConfidence(), node.getTags(), node.getOccurrenceCount());
    }
}
