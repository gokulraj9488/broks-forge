package com.broksforge.modules.platform.service;

import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.repository.AgentRepository;
import com.broksforge.modules.dataset.domain.Dataset;
import com.broksforge.modules.dataset.repository.DatasetRepository;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.organization.domain.Organization;
import com.broksforge.modules.organization.repository.OrganizationRepository;
import com.broksforge.modules.platform.web.dto.GraphEdge;
import com.broksforge.modules.platform.web.dto.GraphNode;
import com.broksforge.modules.platform.web.dto.PlatformGraphResponse;
import com.broksforge.modules.project.domain.Project;
import com.broksforge.modules.project.repository.ProjectRepository;
import com.broksforge.modules.prompt.domain.Prompt;
import com.broksforge.modules.prompt.repository.PromptRepository;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles the engineering graph for an organization from the live domain — real artifacts (organization,
 * projects, providers, models, agents, prompts, datasets, evaluations) and the real relationships between
 * them. Read-only and deterministic: it only reads existing repositories, never writes, and produces the
 * same graph for the same state.
 *
 * <p>The node/edge model is deliberately open: node {@code type} and edge {@code relation} are stable strings,
 * and node ids are {@code "<type>:<id>"}. Future phases add knowledge/claim/decision nodes and provenance
 * edges without changing this contract.
 */
@Service
public class PlatformGraphService {

    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final ProviderRepository providerRepository;
    private final AgentRepository agentRepository;
    private final PromptRepository promptRepository;
    private final DatasetRepository datasetRepository;
    private final EvaluationJobRepository evaluationJobRepository;

    public PlatformGraphService(OrganizationRepository organizationRepository,
                                ProjectRepository projectRepository,
                                ProviderRepository providerRepository,
                                AgentRepository agentRepository,
                                PromptRepository promptRepository,
                                DatasetRepository datasetRepository,
                                EvaluationJobRepository evaluationJobRepository) {
        this.organizationRepository = organizationRepository;
        this.projectRepository = projectRepository;
        this.providerRepository = providerRepository;
        this.agentRepository = agentRepository;
        this.promptRepository = promptRepository;
        this.datasetRepository = datasetRepository;
        this.evaluationJobRepository = evaluationJobRepository;
    }

    @Transactional(readOnly = true)
    public PlatformGraphResponse buildOrganizationGraph(UUID organizationId) {
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        Map<String, GraphEdge> edges = new LinkedHashMap<>();

        String orgNodeId = "org:" + organizationId;
        String orgLabel = organizationRepository.findById(organizationId)
                .map(Organization::getName).orElse("Organization");
        nodes.put(orgNodeId, new GraphNode(orgNodeId, "organization", orgLabel, null, null, null));

        for (Project project : projectRepository.findByOrganizationIdAndDeletedFalse(organizationId, Pageable.unpaged())) {
            String id = "project:" + project.getId();
            nodes.put(id, new GraphNode(id, "project", project.getName(), null, project.getId(), project.getId()));
            edge(edges, orgNodeId, id, "contains");
        }

        for (Provider provider : providerRepository.findByOrganizationIdAndDeletedFalse(organizationId)) {
            String id = "provider:" + provider.getId();
            String subtitle = provider.getType() != null ? provider.getType().name() : null;
            nodes.put(id, new GraphNode(id, "provider", provider.getName(), subtitle,
                    provider.getId(), provider.getProjectId()));
            edge(edges, parent(nodes, provider.getProjectId(), orgNodeId), id, "contains");
        }

        for (Agent agent : agentRepository.findByOrganizationIdAndDeletedFalse(organizationId)) {
            String id = "agent:" + agent.getId();
            nodes.put(id, new GraphNode(id, "agent", agent.getName(), null, agent.getId(), agent.getProjectId()));
            edge(edges, parent(nodes, agent.getProjectId(), orgNodeId), id, "contains");

            String providerNodeId = agent.getProviderId() != null ? "provider:" + agent.getProviderId() : null;
            if (StringUtils.hasText(agent.getModelOverride())) {
                String modelKey = (agent.getProviderId() != null ? agent.getProviderId().toString() : "-")
                        + ":" + agent.getModelOverride();
                String modelNodeId = "model:" + modelKey;
                nodes.putIfAbsent(modelNodeId,
                        new GraphNode(modelNodeId, "model", agent.getModelOverride(), "model", null, agent.getProjectId()));
                if (providerNodeId != null && nodes.containsKey(providerNodeId)) {
                    edge(edges, providerNodeId, modelNodeId, "provides");
                }
                edge(edges, id, modelNodeId, "uses");
            } else if (providerNodeId != null && nodes.containsKey(providerNodeId)) {
                edge(edges, id, providerNodeId, "uses");
            }
        }

        for (Prompt prompt : promptRepository.findByOrganizationIdAndDeletedFalse(organizationId)) {
            String id = "prompt:" + prompt.getId();
            nodes.put(id, new GraphNode(id, "prompt", prompt.getName(), null, prompt.getId(), prompt.getProjectId()));
            edge(edges, parent(nodes, prompt.getProjectId(), orgNodeId), id, "contains");
        }

        for (Dataset dataset : datasetRepository.findByOrganizationIdAndDeletedFalse(organizationId)) {
            String id = "dataset:" + dataset.getId();
            nodes.put(id, new GraphNode(id, "dataset", dataset.getName(), null, dataset.getId(), dataset.getProjectId()));
            edge(edges, parent(nodes, dataset.getProjectId(), orgNodeId), id, "contains");
        }

        for (EvaluationJob job : evaluationJobRepository.findByOrganizationIdAndDeletedFalse(organizationId)) {
            String id = "evaluation:" + job.getId();
            nodes.put(id, new GraphNode(id, "evaluation", job.getName(), null, job.getId(), job.getProjectId()));
            edge(edges, parent(nodes, job.getProjectId(), orgNodeId), id, "contains");
            if (job.getAgentId() != null && nodes.containsKey("agent:" + job.getAgentId())) {
                edge(edges, id, "agent:" + job.getAgentId(), "evaluates");
            }
            if (job.getDatasetId() != null && nodes.containsKey("dataset:" + job.getDatasetId())) {
                edge(edges, id, "dataset:" + job.getDatasetId(), "uses");
            }
        }

        return new PlatformGraphResponse(List.copyOf(nodes.values()), List.copyOf(edges.values()));
    }

    /** The project node when it exists, otherwise the organization root — so no edge ever dangles. */
    private static String parent(Map<String, GraphNode> nodes, UUID projectId, String orgNodeId) {
        String projectNodeId = projectId != null ? "project:" + projectId : null;
        return (projectNodeId != null && nodes.containsKey(projectNodeId)) ? projectNodeId : orgNodeId;
    }

    private static void edge(Map<String, GraphEdge> edges, String source, String target, String relation) {
        String id = source + "->" + target + ":" + relation;
        edges.putIfAbsent(id, new GraphEdge(id, source, target, relation));
    }
}
