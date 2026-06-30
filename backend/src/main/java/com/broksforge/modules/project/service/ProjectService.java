package com.broksforge.modules.project.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.util.SlugGenerator;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.domain.Project;
import com.broksforge.modules.project.repository.ProjectRepository;
import com.broksforge.modules.project.web.ProjectMapper;
import com.broksforge.modules.project.web.dto.CreateProjectRequest;
import com.broksforge.modules.project.web.dto.ProjectResponse;
import com.broksforge.modules.project.web.dto.UpdateProjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Application service for projects. Project visibility and mutation are gated by
 * the caller's organization role:
 * <ul>
 *     <li>read &amp; create &amp; update: any member</li>
 *     <li>delete: ADMIN or higher</li>
 * </ul>
 */
@Slf4j
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final OrganizationAccessService accessService;
    private final ProjectMapper mapper;

    public ProjectService(ProjectRepository projectRepository,
                          OrganizationAccessService accessService,
                          ProjectMapper mapper) {
        this.projectRepository = projectRepository;
        this.accessService = accessService;
        this.mapper = mapper;
    }

    @Transactional
    public ProjectResponse create(UUID actorId, UUID organizationId, CreateProjectRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        String slug = resolveSlug(organizationId, request.slug(), request.name());

        Project project = new Project();
        project.setOrganizationId(organizationId);
        project.setName(request.name().trim());
        project.setSlug(slug);
        project.setDescription(trimToNull(request.description()));
        Project saved = projectRepository.save(project);

        log.info("Project {} ('{}') created in organization {} by {}",
                saved.getId(), slug, organizationId, actorId);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(UUID actorId, UUID organizationId, Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        return PageResponse.from(
                projectRepository.findByOrganizationIdAndDeletedFalse(organizationId, pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID actorId, UUID organizationId, UUID projectId) {
        accessService.requireMembership(organizationId, actorId);
        return mapper.toResponse(getOrThrow(organizationId, projectId));
    }

    @Transactional
    public ProjectResponse update(UUID actorId, UUID organizationId, UUID projectId, UpdateProjectRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        Project project = getOrThrow(organizationId, projectId);

        if (StringUtils.hasText(request.name())) {
            project.setName(request.name().trim());
        }
        if (request.description() != null) {
            project.setDescription(trimToNull(request.description()));
        }
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        return mapper.toResponse(project);
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.ADMIN);
        Project project = getOrThrow(organizationId, projectId);
        project.softDelete(actorId);
        log.info("Project {} soft-deleted in organization {} by {}", projectId, organizationId, actorId);
    }

    /**
     * Verifies a project exists within the organization, throwing 404 otherwise.
     * Exposed for sibling modules (e.g. API keys) that operate on a project.
     */
    @Transactional(readOnly = true)
    public void assertProjectExists(UUID organizationId, UUID projectId) {
        getOrThrow(organizationId, projectId);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private String resolveSlug(UUID organizationId, String requestedSlug, String name) {
        if (StringUtils.hasText(requestedSlug)) {
            String slug = requestedSlug.trim().toLowerCase();
            if (projectRepository.existsByOrganizationIdAndSlugIgnoreCaseAndDeletedFalse(organizationId, slug)) {
                throw new ResourceConflictException(ErrorCode.SLUG_ALREADY_EXISTS,
                        "A project with this slug already exists in the organization");
            }
            return slug;
        }
        return SlugGenerator.uniqueSlug(name,
                candidate -> projectRepository
                        .existsByOrganizationIdAndSlugIgnoreCaseAndDeletedFalse(organizationId, candidate));
    }

    private Project getOrThrow(UUID organizationId, UUID projectId) {
        return projectRepository.findByIdAndOrganizationIdAndDeletedFalse(projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
