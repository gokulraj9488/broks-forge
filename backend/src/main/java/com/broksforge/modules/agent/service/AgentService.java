package com.broksforge.modules.agent.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.util.SlugGenerator;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.domain.AgentCapabilities;
import com.broksforge.modules.agent.domain.AgentTag;
import com.broksforge.modules.agent.repository.AgentCredentialRepository;
import com.broksforge.modules.agent.repository.AgentRepository;
import com.broksforge.modules.agent.repository.AgentSpecifications;
import com.broksforge.modules.agent.repository.AgentTagRepository;
import com.broksforge.modules.agent.web.AgentMapper;
import com.broksforge.modules.agent.web.dto.AgentCapabilitiesDto;
import com.broksforge.modules.agent.web.dto.AgentFilter;
import com.broksforge.modules.agent.web.dto.AgentResponse;
import com.broksforge.modules.agent.web.dto.AgentSummaryResponse;
import com.broksforge.modules.agent.web.dto.RegisterAgentRequest;
import com.broksforge.modules.agent.web.dto.UpdateAgentRequest;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for the agent aggregate root: registration, search,
 * update, archival and soft deletion. Tag and capability management are part of
 * the aggregate and handled here. Version, credential and health concerns are
 * delegated to their own services to keep responsibilities focused.
 */
@Slf4j
@Service
public class AgentService {

    private final AgentRepository agentRepository;
    private final AgentTagRepository tagRepository;
    private final AgentCredentialRepository credentialRepository;
    private final AgentAccessGuard accessGuard;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final AgentMapper mapper;

    public AgentService(AgentRepository agentRepository,
                        AgentTagRepository tagRepository,
                        AgentCredentialRepository credentialRepository,
                        AgentAccessGuard accessGuard,
                        OrganizationAccessService accessService,
                        ProjectService projectService,
                        AgentMapper mapper) {
        this.agentRepository = agentRepository;
        this.tagRepository = tagRepository;
        this.credentialRepository = credentialRepository;
        this.accessGuard = accessGuard;
        this.accessService = accessService;
        this.projectService = projectService;
        this.mapper = mapper;
    }

    @Transactional
    public AgentResponse register(UUID actorId, UUID organizationId, UUID projectId, RegisterAgentRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        projectService.assertProjectExists(organizationId, projectId);

        String slug = resolveSlug(projectId, request.slug(), request.name());

        Agent agent = new Agent();
        agent.setOrganizationId(organizationId);
        agent.setProjectId(projectId);
        agent.setOwnerId(actorId);
        agent.setName(request.name().trim());
        agent.setSlug(slug);
        agent.setDescription(trimToNull(request.description()));
        agent.setVisibility(request.visibility());
        agent.setFramework(request.framework());
        agent.setLanguage(request.language());
        agent.setEndpointUrl(request.endpointUrl().trim());
        agent.setAuthType(request.authType());
        applyCapabilities(agent.getCapabilities(), request.capabilities());

        Agent saved = agentRepository.save(agent);
        List<String> tags = replaceTags(saved, request.tags());

        log.info("Agent {} ('{}') registered in project {} by {}", saved.getId(), slug, projectId, actorId);
        // Fresh agent: no credential yet, so this is false unless the agent needs none (NONE auth).
        return mapper.toResponse(saved, tags, credentialConfigured(saved));
    }

    @Transactional(readOnly = true)
    public PageResponse<AgentSummaryResponse> search(UUID actorId, UUID organizationId, UUID projectId,
                                                     AgentFilter filter, Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);

        Specification<Agent> spec = AgentSpecifications.build(
                projectId, filter.q(), filter.framework(), filter.language(),
                filter.visibility(), filter.status(), filter.healthStatus(), filter.tag());
        Page<Agent> page = agentRepository.findAll(spec, pageable);

        List<UUID> agentIds = page.getContent().stream().map(Agent::getId).toList();
        Map<UUID, List<String>> tagsByAgent = loadTagsForAgents(agentIds);
        Set<UUID> configured = agentIds.isEmpty()
                ? Set.of()
                : new HashSet<>(credentialRepository.findAgentIdsWithActiveCredential(agentIds));

        return PageResponse.from(page.map(agent ->
                mapper.toSummary(agent, tagsByAgent.getOrDefault(agent.getId(), List.of()),
                        agent.getAuthType() == AgentAuthType.NONE || configured.contains(agent.getId()))));
    }

    @Transactional(readOnly = true)
    public AgentResponse get(UUID actorId, UUID organizationId, UUID projectId, UUID agentId) {
        Agent agent = accessGuard.requireReadable(organizationId, projectId, agentId, actorId);
        return mapper.toResponse(agent, loadTags(agentId), credentialConfigured(agent));
    }

    @Transactional
    public AgentResponse update(UUID actorId, UUID organizationId, UUID projectId, UUID agentId,
                                UpdateAgentRequest request) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(agent);

        if (StringUtils.hasText(request.name())) {
            agent.setName(request.name().trim());
        }
        if (request.description() != null) {
            agent.setDescription(trimToNull(request.description()));
        }
        if (request.visibility() != null) {
            agent.setVisibility(request.visibility());
        }
        if (request.framework() != null) {
            agent.setFramework(request.framework());
        }
        if (request.language() != null) {
            agent.setLanguage(request.language());
        }
        if (StringUtils.hasText(request.endpointUrl())) {
            agent.setEndpointUrl(request.endpointUrl().trim());
        }
        if (request.authType() != null) {
            agent.setAuthType(request.authType());
        }
        if (request.capabilities() != null) {
            applyCapabilities(agent.getCapabilities(), request.capabilities());
        }

        List<String> tags = request.tags() != null ? replaceTags(agent, request.tags()) : loadTags(agentId);
        log.info("Agent {} updated in project {} by {}", agentId, projectId, actorId);
        return mapper.toResponse(agent, tags, credentialConfigured(agent));
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId, UUID agentId) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.ADMIN);
        agent.softDelete(actorId);
        log.info("Agent {} soft-deleted in project {} by {}", agentId, projectId, actorId);
    }

    @Transactional
    public AgentResponse archive(UUID actorId, UUID organizationId, UUID projectId, UUID agentId) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.MEMBER);
        agent.archive();
        log.info("Agent {} archived in project {} by {}", agentId, projectId, actorId);
        return mapper.toResponse(agent, loadTags(agentId), credentialConfigured(agent));
    }

    @Transactional
    public AgentResponse unarchive(UUID actorId, UUID organizationId, UUID projectId, UUID agentId) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.MEMBER);
        agent.unarchive();
        log.info("Agent {} unarchived in project {} by {}", agentId, projectId, actorId);
        return mapper.toResponse(agent, loadTags(agentId), credentialConfigured(agent));
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private void applyCapabilities(AgentCapabilities target, AgentCapabilitiesDto dto) {
        if (dto == null) {
            return;
        }
        target.setStreaming(dto.streaming());
        target.setMemory(dto.memory());
        target.setRag(dto.rag());
        target.setToolCalling(dto.toolCalling());
        target.setStructuredOutput(dto.structuredOutput());
        target.setReasoning(dto.reasoning());
        target.setMultiAgent(dto.multiAgent());
        target.setCustomMetadata(dto.customMetadata() != null
                ? new LinkedHashMap<>(dto.customMetadata())
                : new LinkedHashMap<>());
    }

    private List<String> replaceTags(Agent agent, Set<String> requested) {
        if (requested == null) {
            return loadTags(agent.getId());
        }
        tagRepository.deleteByAgentId(agent.getId());

        List<String> normalised = requested.stream()
                .filter(StringUtils::hasText)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        for (String label : normalised) {
            AgentTag tag = new AgentTag();
            tag.setAgentId(agent.getId());
            tag.setOrganizationId(agent.getOrganizationId());
            tag.setLabel(label);
            tagRepository.save(tag);
        }
        return normalised;
    }

    private List<String> loadTags(UUID agentId) {
        return tagRepository.findByAgentIdOrderByLabelAsc(agentId).stream()
                .map(AgentTag::getLabel)
                .toList();
    }

    private Map<UUID, List<String>> loadTagsForAgents(List<UUID> agentIds) {
        if (agentIds.isEmpty()) {
            return Map.of();
        }
        return tagRepository.findByAgentIdInOrderByLabelAsc(agentIds).stream()
                .collect(Collectors.groupingBy(
                        AgentTag::getAgentId,
                        Collectors.mapping(AgentTag::getLabel, Collectors.toList())));
    }

    private String resolveSlug(UUID projectId, String requestedSlug, String name) {
        if (StringUtils.hasText(requestedSlug)) {
            String slug = requestedSlug.trim().toLowerCase(Locale.ROOT);
            if (agentRepository.existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(projectId, slug)) {
                throw new ResourceConflictException(ErrorCode.SLUG_ALREADY_EXISTS,
                        "An agent with this slug already exists in the project");
            }
            return slug;
        }
        return SlugGenerator.uniqueSlug(name,
                candidate -> agentRepository.existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(projectId, candidate));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Whether the agent's credential situation is satisfied — i.e. it needs no
     * authentication (NONE) or it has an active credential. Drives the
     * non-sensitive {@code credentialConfigured} readiness flag on responses.
     * Within the agent aggregate, so reading the credential repository here is
     * a same-module concern, not a cross-module dependency.
     */
    private boolean credentialConfigured(Agent agent) {
        return agent.getAuthType() == AgentAuthType.NONE
                || credentialRepository.existsByAgentIdAndActiveTrue(agent.getId());
    }
}
