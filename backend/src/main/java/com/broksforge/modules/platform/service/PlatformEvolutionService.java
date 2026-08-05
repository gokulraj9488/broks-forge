package com.broksforge.modules.platform.service;

import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.agent.repository.AgentVersionRepository;
import com.broksforge.modules.dataset.repository.DatasetVersionRepository;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.platform.web.dto.ArtifactEvolutionResponse;
import com.broksforge.modules.platform.web.dto.EvolutionEvidence;
import com.broksforge.modules.platform.web.dto.EvolutionRef;
import com.broksforge.modules.platform.web.dto.EvolutionRevision;
import com.broksforge.modules.platform.web.dto.GraphEdge;
import com.broksforge.modules.platform.web.dto.GraphNode;
import com.broksforge.modules.platform.web.dto.PlatformGraphResponse;
import com.broksforge.modules.prompt.repository.PromptVersionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Derives the engineering evolution of a single artifact from the live model — no new storage, one source of
 * truth. Relationships (dependencies, dependents, transitive impact) are read from the same graph the Forge
 * Graph renders; historical revisions come from the artifacts' real version records; evidence comes from the
 * evaluations that referenced the artifact. Everything is real; nothing is fabricated.
 */
@Service
public class PlatformEvolutionService {

    /** Relationship verbs where {@code source depends on target}. */
    private static final Set<String> DEPENDENCY_RELATIONS = Set.of("uses", "evaluates");

    private final PlatformGraphService graph;
    private final AgentVersionRepository agentVersionRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final EvaluationJobRepository evaluationJobRepository;

    public PlatformEvolutionService(PlatformGraphService graph,
                                    AgentVersionRepository agentVersionRepository,
                                    PromptVersionRepository promptVersionRepository,
                                    DatasetVersionRepository datasetVersionRepository,
                                    EvaluationJobRepository evaluationJobRepository) {
        this.graph = graph;
        this.agentVersionRepository = agentVersionRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.evaluationJobRepository = evaluationJobRepository;
    }

    @Transactional(readOnly = true)
    public ArtifactEvolutionResponse evolutionOf(UUID organizationId, String type, UUID entityId) {
        if (!PlatformRegistryService.TYPES.contains(type)) {
            throw ResourceNotFoundException.of("Artifact", entityId);
        }
        PlatformGraphResponse g = graph.buildOrganizationGraph(organizationId);
        Map<String, GraphNode> byId = new LinkedHashMap<>();
        g.nodes().forEach(n -> byId.put(n.id(), n));

        String nodeId = type + ":" + entityId;
        GraphNode node = byId.get(nodeId);
        if (node == null) {
            throw ResourceNotFoundException.of("Artifact", entityId);
        }

        List<EvolutionRef> dependencies = new ArrayList<>();
        List<EvolutionRef> dependents = new ArrayList<>();
        Map<String, Set<String>> dependentOf = new HashMap<>(); // dependency -> {things that depend on it}

        for (GraphEdge e : g.edges()) {
            String rel = e.relation();
            String dependentNode;
            String dependencyNode;
            if (DEPENDENCY_RELATIONS.contains(rel)) {
                dependentNode = e.source();   // source uses/evaluates target
                dependencyNode = e.target();
            } else if ("provides".equals(rel)) {
                dependentNode = e.target();    // provider provides model => model depends on provider
                dependencyNode = e.source();
            } else {
                continue; // structural "contains" and anything else is not a dependency edge
            }
            dependentOf.computeIfAbsent(dependencyNode, k -> new LinkedHashSet<>()).add(dependentNode);

            if (dependentNode.equals(nodeId) && byId.containsKey(dependencyNode)) {
                dependencies.add(ref(byId.get(dependencyNode), rel));
            }
            if (dependencyNode.equals(nodeId) && byId.containsKey(dependentNode)) {
                dependents.add(ref(byId.get(dependentNode), rel));
            }
        }

        int impactCount = transitiveImpact(nodeId, dependentOf);
        List<EvolutionRevision> history = history(type, entityId);
        List<EvolutionEvidence> evidence = evidence(organizationId, type, entityId);

        return new ArtifactEvolutionResponse(ref(node, null), dependencies, dependents, impactCount, history, evidence);
    }

    /** Count of artifacts transitively downstream of {@code start} (everything a change here could affect). */
    private int transitiveImpact(String start, Map<String, Set<String>> dependentOf) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            for (String d : dependentOf.getOrDefault(queue.poll(), Set.of())) {
                if (visited.add(d)) {
                    queue.add(d);
                }
            }
        }
        visited.remove(start);
        return visited.size();
    }

    private List<EvolutionRevision> history(String type, UUID entityId) {
        PageRequest first100 = PageRequest.of(0, 100);
        return switch (type) {
            case "agent" -> agentVersionRepository
                    .findByAgentId(entityId, PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .stream()
                    .map(v -> new EvolutionRevision(v.getVersionNumber(), v.getReleaseNotes(), v.isActive(),
                            v.getCreatedAt()))
                    .toList();
            case "prompt" -> promptVersionRepository.findByPromptIdOrderByVersionNumberDesc(entityId, first100)
                    .stream()
                    .map(v -> new EvolutionRevision("v" + v.getVersionNumber(), v.getNotes(), v.isActive(),
                            v.getCreatedAt()))
                    .toList();
            case "dataset" -> datasetVersionRepository.findByDatasetIdOrderByVersionNumberDesc(entityId, first100)
                    .stream()
                    .map(v -> new EvolutionRevision("v" + v.getVersionNumber(), null, false, v.getCreatedAt()))
                    .toList();
            default -> List.of();
        };
    }

    private List<EvolutionEvidence> evidence(UUID organizationId, String type, UUID entityId) {
        if (!Set.of("agent", "dataset", "prompt").contains(type)) {
            return List.of();
        }
        return evaluationJobRepository.findByOrganizationIdAndDeletedFalse(organizationId).stream()
                .filter(j -> switch (type) {
                    case "agent" -> entityId.equals(j.getAgentId());
                    case "dataset" -> entityId.equals(j.getDatasetId());
                    case "prompt" -> entityId.equals(j.getPromptId());
                    default -> false;
                })
                .sorted(Comparator.comparing(EvaluationJob::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(j -> new EvolutionEvidence("evaluation:" + j.getId(), "evaluation", j.getName(),
                        j.getStatus() != null ? j.getStatus().name() : null, j.getId(), j.getProjectId(),
                        j.getCreatedAt()))
                .toList();
    }

    private static EvolutionRef ref(GraphNode n, String relation) {
        return new EvolutionRef(n.id(), n.type(), n.label(), n.entityId(), n.projectId(), relation);
    }
}
