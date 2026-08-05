package com.broksforge.modules.brok.service;

import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.agent.repository.AgentRepository;
import com.broksforge.modules.dataset.repository.DatasetRepository;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.platform.service.PlatformIntelligenceService;
import com.broksforge.modules.platform.web.dto.KnowledgeObject;
import com.broksforge.modules.project.domain.Project;
import com.broksforge.modules.project.repository.ProjectRepository;
import com.broksforge.modules.prompt.repository.PromptRepository;
import com.broksforge.modules.provider.repository.ProviderRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reads the engineering record Brok will reason over — once per question, from the same repositories
 * and the same derived-knowledge projection every other surface uses.
 *
 * <p>This is the only place Brok touches data. Keeping the read in one class is what guarantees that
 * an answer, a recommendation and a brief produced in the same request all describe the same instant of the
 * system, and that Brok never introduces a second source of truth.
 */
@Service
public class BrokRecordReader {

    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;
    private final PromptRepository promptRepository;
    private final DatasetRepository datasetRepository;
    private final ProviderRepository providerRepository;
    private final EvaluationJobRepository evaluationJobRepository;
    private final PlatformIntelligenceService intelligence;

    public BrokRecordReader(ProjectRepository projectRepository,
                               AgentRepository agentRepository,
                               PromptRepository promptRepository,
                               DatasetRepository datasetRepository,
                               ProviderRepository providerRepository,
                               EvaluationJobRepository evaluationJobRepository,
                               PlatformIntelligenceService intelligence) {
        this.projectRepository = projectRepository;
        this.agentRepository = agentRepository;
        this.promptRepository = promptRepository;
        this.datasetRepository = datasetRepository;
        this.providerRepository = providerRepository;
        this.evaluationJobRepository = evaluationJobRepository;
        this.intelligence = intelligence;
    }

    /**
     * Snapshots the organization's engineering record, optionally narrowed to one project.
     *
     * @param projectId when given, every artifact and evaluation outside that project is excluded so the
     *                  Brok answers about the workspace the engineer is actually looking at. An unknown
     *                  project is a 404 rather than a silently empty answer — a wrong scope must never be
     *                  reported as "nothing is happening".
     */
    @Transactional(readOnly = true)
    public BrokRecord read(UUID organizationId, UUID projectId) {
        Map<UUID, String> projectNames = new LinkedHashMap<>();
        for (Project project : projectRepository.findByOrganizationIdAndDeletedFalse(organizationId, Pageable.unpaged())) {
            projectNames.put(project.getId(), project.getName());
        }
        if (projectId != null && !projectNames.containsKey(projectId)) {
            throw ResourceNotFoundException.of("Project", projectId);
        }

        List<KnowledgeObject> knowledge = intelligence.catalog(organizationId).stream()
                .filter(k -> projectId == null || projectId.equals(k.projectId()))
                .toList();

        return new BrokRecord(
                organizationId,
                projectId,
                projectNames,
                scoped(agentRepository.findByOrganizationIdAndDeletedFalse(organizationId), projectId,
                        a -> a.getProjectId()),
                scoped(promptRepository.findByOrganizationIdAndDeletedFalse(organizationId), projectId,
                        p -> p.getProjectId()),
                scoped(datasetRepository.findByOrganizationIdAndDeletedFalse(organizationId), projectId,
                        d -> d.getProjectId()),
                scoped(providerRepository.findByOrganizationIdAndDeletedFalse(organizationId), projectId,
                        p -> p.getProjectId()),
                scoped(evaluationJobRepository.findByOrganizationIdAndDeletedFalse(organizationId), projectId,
                        j -> j.getProjectId()),
                knowledge,
                Instant.now());
    }

    private static <T> List<T> scoped(List<T> items, UUID projectId, Function<T, UUID> project) {
        return projectId == null ? List.copyOf(items)
                : items.stream().filter(i -> projectId.equals(project.apply(i))).toList();
    }
}
