package com.broksforge.modules.platform.service;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.agent.repository.AgentRepository;
import com.broksforge.modules.dataset.repository.DatasetRepository;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.platform.web.dto.RegistryItemResponse;
import com.broksforge.modules.platform.web.dto.RegistryTypeCount;
import com.broksforge.modules.project.domain.Project;
import com.broksforge.modules.project.repository.ProjectRepository;
import com.broksforge.modules.prompt.repository.PromptRepository;
import com.broksforge.modules.provider.repository.ProviderRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles the unified engineering registry for an organization from the live domain — a single, read-only
 * catalog over the existing repositories (no new storage, no second source of truth). Filtering, search,
 * sorting and pagination happen server-side so the client never loads the whole catalog.
 *
 * <p>The registry is discovery-only: it exposes catalog metadata and the ids needed to deep-link to each
 * artifact's existing management page. New artifact types plug in by adding a mapper here — the DTO contract
 * and the type list are open.
 */
@Service
public class PlatformRegistryService {

    /** The artifact kinds the registry currently catalogs, in display order. */
    public static final List<String> TYPES = List.of("project", "provider", "agent", "prompt", "dataset", "evaluation");

    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectRepository projectRepository;
    private final ProviderRepository providerRepository;
    private final AgentRepository agentRepository;
    private final PromptRepository promptRepository;
    private final DatasetRepository datasetRepository;
    private final EvaluationJobRepository evaluationJobRepository;

    public PlatformRegistryService(ProjectRepository projectRepository,
                                   ProviderRepository providerRepository,
                                   AgentRepository agentRepository,
                                   PromptRepository promptRepository,
                                   DatasetRepository datasetRepository,
                                   EvaluationJobRepository evaluationJobRepository) {
        this.projectRepository = projectRepository;
        this.providerRepository = providerRepository;
        this.agentRepository = agentRepository;
        this.promptRepository = promptRepository;
        this.datasetRepository = datasetRepository;
        this.evaluationJobRepository = evaluationJobRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<RegistryItemResponse> list(UUID organizationId, String q, String type, UUID projectId,
                                                   UUID providerId, String tag, String sort, int page, int size) {
        String query = normalize(q);
        String tagFilter = normalize(tag);

        List<RegistryItemResponse> items = assemble(organizationId).stream()
                .filter(i -> type == null || type.isBlank() || i.type().equals(type))
                .filter(i -> projectId == null || projectId.equals(i.projectId()))
                .filter(i -> providerId == null || providerId.equals(i.providerId()))
                .filter(i -> query == null || i.name().toLowerCase(Locale.ROOT).contains(query))
                .filter(i -> tagFilter == null
                        || i.tags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(tagFilter)))
                .sorted(comparator(sort))
                .toList();

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, items.size());
        int to = Math.min(from + safeSize, items.size());
        List<RegistryItemResponse> content = items.subList(from, to);

        return PageResponse.from(new PageImpl<>(content, PageRequest.of(safePage, safeSize), items.size()));
    }

    @Transactional(readOnly = true)
    public List<RegistryTypeCount> types(UUID organizationId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        TYPES.forEach(t -> counts.put(t, 0L));
        for (RegistryItemResponse item : assemble(organizationId)) {
            counts.merge(item.type(), 1L, Long::sum);
        }
        return counts.entrySet().stream().map(e -> new RegistryTypeCount(e.getKey(), e.getValue())).toList();
    }

    // ---- assembly ----

    private List<RegistryItemResponse> assemble(UUID organizationId) {
        List<Project> projects = projectRepository
                .findByOrganizationIdAndDeletedFalse(organizationId, Pageable.unpaged()).getContent();
        Map<UUID, String> projectNames = new LinkedHashMap<>();
        projects.forEach(p -> projectNames.put(p.getId(), p.getName()));

        List<RegistryItemResponse> items = new ArrayList<>();

        for (Project p : projects) {
            items.add(new RegistryItemResponse("project:" + p.getId(), "project", p.getName(),
                    p.getStatus() != null ? p.getStatus().name() : null, p.getId(), p.getId(), p.getName(),
                    null, List.of(), p.getCreatedAt()));
        }
        for (var pr : providerRepository.findByOrganizationIdAndDeletedFalse(organizationId)) {
            items.add(new RegistryItemResponse("provider:" + pr.getId(), "provider", pr.getName(),
                    pr.getType() != null ? pr.getType().name() : null, pr.getId(), pr.getProjectId(),
                    projectNames.get(pr.getProjectId()), pr.getId(), List.of(), pr.getCreatedAt()));
        }
        for (var a : agentRepository.findByOrganizationIdAndDeletedFalse(organizationId)) {
            items.add(new RegistryItemResponse("agent:" + a.getId(), "agent", a.getName(),
                    a.getStatus() != null ? a.getStatus().name() : null, a.getId(), a.getProjectId(),
                    projectNames.get(a.getProjectId()), a.getProviderId(), List.of(), a.getCreatedAt()));
        }
        for (var pr : promptRepository.findByOrganizationIdAndDeletedFalse(organizationId)) {
            items.add(new RegistryItemResponse("prompt:" + pr.getId(), "prompt", pr.getName(),
                    pr.getStatus() != null ? pr.getStatus().name() : null, pr.getId(), pr.getProjectId(),
                    projectNames.get(pr.getProjectId()), null, safeTags(pr.getTags()), pr.getCreatedAt()));
        }
        for (var d : datasetRepository.findByOrganizationIdAndDeletedFalse(organizationId)) {
            items.add(new RegistryItemResponse("dataset:" + d.getId(), "dataset", d.getName(),
                    d.getStatus() != null ? d.getStatus().name() : null, d.getId(), d.getProjectId(),
                    projectNames.get(d.getProjectId()), null, safeTags(d.getTags()), d.getCreatedAt()));
        }
        for (var j : evaluationJobRepository.findByOrganizationIdAndDeletedFalse(organizationId)) {
            items.add(new RegistryItemResponse("evaluation:" + j.getId(), "evaluation", j.getName(),
                    j.getStatus() != null ? j.getStatus().name() : null, j.getId(), j.getProjectId(),
                    projectNames.get(j.getProjectId()), null, List.of(), j.getCreatedAt()));
        }
        return items;
    }

    private static Comparator<RegistryItemResponse> comparator(String sort) {
        Comparator<RegistryItemResponse> byName =
                Comparator.comparing(i -> i.name() == null ? "" : i.name().toLowerCase(Locale.ROOT));
        Comparator<RegistryItemResponse> byCreated =
                Comparator.comparing(RegistryItemResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        return switch (sort == null ? "recent" : sort) {
            case "name" -> byName;
            case "name_desc" -> byName.reversed();
            case "oldest" -> byCreated;
            default -> byCreated.reversed(); // "recent"
        };
    }

    private static List<String> safeTags(List<String> tags) {
        return tags == null ? List.of() : List.copyOf(tags);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }
}
