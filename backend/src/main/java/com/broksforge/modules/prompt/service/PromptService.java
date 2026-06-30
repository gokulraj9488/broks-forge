package com.broksforge.modules.prompt.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.util.SlugGenerator;
import com.broksforge.common.util.TemplateVariables;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import com.broksforge.modules.prompt.domain.Prompt;
import com.broksforge.modules.prompt.domain.PromptVersion;
import com.broksforge.modules.prompt.repository.PromptRepository;
import com.broksforge.modules.prompt.repository.PromptSpecifications;
import com.broksforge.modules.prompt.repository.PromptVersionRepository;
import com.broksforge.modules.prompt.web.PromptMapper;
import com.broksforge.modules.prompt.web.dto.CreatePromptRequest;
import com.broksforge.modules.prompt.web.dto.CreatePromptVersionRequest;
import com.broksforge.modules.prompt.web.dto.PromptComparisonResponse;
import com.broksforge.modules.prompt.web.dto.PromptFilter;
import com.broksforge.modules.prompt.web.dto.PromptResponse;
import com.broksforge.modules.prompt.web.dto.PromptSummaryResponse;
import com.broksforge.modules.prompt.web.dto.PromptVersionResponse;
import com.broksforge.modules.prompt.web.dto.UpdatePromptRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for the prompt aggregate: library CRUD, immutable versioning,
 * activation/rollback and version comparison. Variables are derived from the
 * template at version-creation time; templates are stored verbatim and rendered as
 * pure data elsewhere (see ADR 0008).
 */
@Slf4j
@Service
public class PromptService {

    private final PromptRepository promptRepository;
    private final PromptVersionRepository versionRepository;
    private final PromptAccessGuard accessGuard;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final PromptMapper mapper;

    public PromptService(PromptRepository promptRepository,
                         PromptVersionRepository versionRepository,
                         PromptAccessGuard accessGuard,
                         OrganizationAccessService accessService,
                         ProjectService projectService,
                         PromptMapper mapper) {
        this.promptRepository = promptRepository;
        this.versionRepository = versionRepository;
        this.accessGuard = accessGuard;
        this.accessService = accessService;
        this.projectService = projectService;
        this.mapper = mapper;
    }

    @Transactional
    public PromptResponse create(UUID actorId, UUID organizationId, UUID projectId, CreatePromptRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        projectService.assertProjectExists(organizationId, projectId);

        Prompt prompt = new Prompt();
        prompt.setOrganizationId(organizationId);
        prompt.setProjectId(projectId);
        prompt.setOwnerId(actorId);
        prompt.setName(request.name().trim());
        prompt.setSlug(resolveSlug(projectId, request.slug(), request.name()));
        prompt.setDescription(trimToNull(request.description()));
        prompt.setTags(normalizeTags(request.tags()));

        Prompt saved = promptRepository.save(prompt);
        log.info("Prompt {} ('{}') created in project {} by {}", saved.getId(), saved.getSlug(), projectId, actorId);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<PromptSummaryResponse> search(UUID actorId, UUID organizationId, UUID projectId,
                                                      PromptFilter filter, Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        Specification<Prompt> spec = PromptSpecifications.build(projectId, filter.q(), filter.status(), filter.tag());
        return PageResponse.from(promptRepository.findAll(spec, pageable), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PromptResponse get(UUID actorId, UUID organizationId, UUID projectId, UUID promptId) {
        return mapper.toResponse(accessGuard.requireReadable(organizationId, projectId, promptId, actorId));
    }

    @Transactional
    public PromptResponse update(UUID actorId, UUID organizationId, UUID projectId, UUID promptId,
                                 UpdatePromptRequest request) {
        Prompt prompt = accessGuard.requireManageable(organizationId, projectId, promptId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(prompt);

        if (StringUtils.hasText(request.name())) {
            prompt.setName(request.name().trim());
        }
        if (request.description() != null) {
            prompt.setDescription(trimToNull(request.description()));
        }
        if (request.status() != null) {
            prompt.setStatus(request.status());
        }
        if (request.tags() != null) {
            prompt.setTags(normalizeTags(request.tags()));
        }
        return mapper.toResponse(prompt);
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId, UUID promptId) {
        Prompt prompt = accessGuard.requireManageable(organizationId, projectId, promptId, actorId,
                OrganizationRole.ADMIN);
        prompt.softDelete(actorId);
        log.info("Prompt {} soft-deleted in project {} by {}", promptId, projectId, actorId);
    }

    @Transactional
    public PromptResponse archive(UUID actorId, UUID organizationId, UUID projectId, UUID promptId) {
        Prompt prompt = accessGuard.requireManageable(organizationId, projectId, promptId, actorId,
                OrganizationRole.MEMBER);
        prompt.archive();
        return mapper.toResponse(prompt);
    }

    @Transactional
    public PromptResponse unarchive(UUID actorId, UUID organizationId, UUID projectId, UUID promptId) {
        Prompt prompt = accessGuard.requireManageable(organizationId, projectId, promptId, actorId,
                OrganizationRole.MEMBER);
        prompt.unarchive();
        return mapper.toResponse(prompt);
    }

    // ----------------------------------------------------------------------
    // Versions
    // ----------------------------------------------------------------------

    @Transactional
    public PromptVersionResponse createVersion(UUID actorId, UUID organizationId, UUID projectId, UUID promptId,
                                               CreatePromptVersionRequest request) {
        Prompt prompt = accessGuard.requireManageable(organizationId, projectId, promptId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(prompt);

        int nextNumber = prompt.getLatestVersionNumber() + 1;
        PromptVersion version = new PromptVersion();
        version.setPromptId(promptId);
        version.setOrganizationId(organizationId);
        version.setProjectId(projectId);
        version.setVersionNumber(nextNumber);
        version.setTemplate(request.template());
        version.setVariables(new ArrayList<>(TemplateVariables.extract(request.template())));
        version.setNotes(trimToNull(request.notes()));
        version.setProvider(request.provider());
        version.setModel(trimToNull(request.model()));
        PromptVersion saved = versionRepository.save(version);

        prompt.setLatestVersionNumber(nextNumber);
        if (Boolean.TRUE.equals(request.activate()) || prompt.getCurrentActiveVersionId() == null) {
            activateInternal(prompt, saved);
        }
        log.info("Prompt {} version {} created by {}", promptId, nextNumber, actorId);
        return mapper.toVersionResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<PromptVersionResponse> listVersions(UUID actorId, UUID organizationId, UUID projectId,
                                                            UUID promptId, Pageable pageable) {
        accessGuard.requireReadable(organizationId, projectId, promptId, actorId);
        return PageResponse.from(
                versionRepository.findByPromptIdOrderByVersionNumberDesc(promptId, pageable),
                mapper::toVersionResponse);
    }

    @Transactional(readOnly = true)
    public PromptVersionResponse getVersion(UUID actorId, UUID organizationId, UUID projectId, UUID promptId,
                                            UUID versionId) {
        accessGuard.requireReadable(organizationId, projectId, promptId, actorId);
        return mapper.toVersionResponse(getVersionOrThrow(promptId, versionId));
    }

    @Transactional
    public PromptVersionResponse activate(UUID actorId, UUID organizationId, UUID projectId, UUID promptId,
                                          UUID versionId) {
        Prompt prompt = accessGuard.requireManageable(organizationId, projectId, promptId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(prompt);
        PromptVersion version = getVersionOrThrow(promptId, versionId);
        activateInternal(prompt, version);
        log.info("Prompt {} activated version {} by {}", promptId, version.getVersionNumber(), actorId);
        return mapper.toVersionResponse(version);
    }

    @Transactional
    public PromptVersionResponse rollback(UUID actorId, UUID organizationId, UUID projectId, UUID promptId,
                                          UUID versionId) {
        Prompt prompt = accessGuard.requireManageable(organizationId, projectId, promptId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(prompt);
        PromptVersion version = getVersionOrThrow(promptId, versionId);
        activateInternal(prompt, version);
        log.warn("Prompt {} rolled back to version {} by {}", promptId, version.getVersionNumber(), actorId);
        return mapper.toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public PromptComparisonResponse compare(UUID actorId, UUID organizationId, UUID projectId, UUID promptId,
                                            UUID fromVersionId, UUID toVersionId) {
        accessGuard.requireReadable(organizationId, projectId, promptId, actorId);
        PromptVersion from = getVersionOrThrow(promptId, fromVersionId);
        PromptVersion to = getVersionOrThrow(promptId, toVersionId);

        Set<String> fromVars = new LinkedHashSet<>(from.getVariables());
        Set<String> toVars = new LinkedHashSet<>(to.getVariables());

        List<String> added = toVars.stream().filter(v -> !fromVars.contains(v)).toList();
        List<String> removed = fromVars.stream().filter(v -> !toVars.contains(v)).toList();
        List<String> common = fromVars.stream().filter(toVars::contains).toList();

        return new PromptComparisonResponse(
                promptId,
                mapper.toVersionResponse(from),
                mapper.toVersionResponse(to),
                added,
                removed,
                common,
                from.getTemplate().equals(to.getTemplate()));
    }

    /**
     * Resolves a prompt version for execution by sibling modules (e.g. evaluation):
     * the explicit version when supplied, otherwise the active version.
     */
    @Transactional(readOnly = true)
    public PromptVersionResponse getVersionForExecution(UUID actorId, UUID organizationId, UUID projectId,
                                                        UUID promptId, UUID versionId) {
        Prompt prompt = accessGuard.requireReadable(organizationId, projectId, promptId, actorId);
        UUID effectiveId = versionId != null ? versionId : prompt.getCurrentActiveVersionId();
        if (effectiveId == null) {
            throw new ResourceConflictException(ErrorCode.PROMPT_NO_ACTIVE_VERSION,
                    "Prompt has no active version; create or activate one first");
        }
        return mapper.toVersionResponse(getVersionOrThrow(promptId, effectiveId));
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private void activateInternal(Prompt prompt, PromptVersion target) {
        for (PromptVersion active : versionRepository.findByPromptIdAndActiveTrue(prompt.getId())) {
            if (!active.getId().equals(target.getId())) {
                active.setActive(false);
            }
        }
        target.setActive(true);
        prompt.setCurrentActiveVersionId(target.getId());
    }

    private PromptVersion getVersionOrThrow(UUID promptId, UUID versionId) {
        return versionRepository.findByIdAndPromptId(versionId, promptId)
                .orElseThrow(() -> ResourceNotFoundException.of("Prompt version", versionId));
    }

    private String resolveSlug(UUID projectId, String requestedSlug, String name) {
        if (StringUtils.hasText(requestedSlug)) {
            String slug = requestedSlug.trim().toLowerCase(Locale.ROOT);
            if (promptRepository.existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(projectId, slug)) {
                throw new ResourceConflictException(ErrorCode.SLUG_ALREADY_EXISTS,
                        "A prompt with this slug already exists in the project");
            }
            return slug;
        }
        return SlugGenerator.uniqueSlug(name,
                candidate -> promptRepository.existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(projectId, candidate));
    }

    private List<String> normalizeTags(Set<String> requested) {
        if (requested == null) {
            return new ArrayList<>();
        }
        return requested.stream()
                .filter(StringUtils::hasText)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
