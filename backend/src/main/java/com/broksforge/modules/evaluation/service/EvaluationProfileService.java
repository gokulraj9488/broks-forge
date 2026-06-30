package com.broksforge.modules.evaluation.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.util.SlugGenerator;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.evaluation.domain.EvaluationProfile;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.repository.EvaluationProfileRepository;
import com.broksforge.modules.evaluation.web.EvaluationMapper;
import com.broksforge.modules.evaluation.web.dto.CreateEvaluationProfileRequest;
import com.broksforge.modules.evaluation.web.dto.EvaluationProfileResponse;
import com.broksforge.modules.evaluation.web.dto.MetricSpecDto;
import com.broksforge.modules.evaluation.web.dto.UpdateEvaluationProfileRequest;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.UUID;

/**
 * CRUD for reusable evaluation profiles (metric rubrics).
 */
@Slf4j
@Service
public class EvaluationProfileService {

    private final EvaluationProfileRepository profileRepository;
    private final EvaluationAccessGuard accessGuard;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final EvaluationMapper mapper;

    public EvaluationProfileService(EvaluationProfileRepository profileRepository,
                                    EvaluationAccessGuard accessGuard,
                                    OrganizationAccessService accessService,
                                    ProjectService projectService,
                                    EvaluationMapper mapper) {
        this.profileRepository = profileRepository;
        this.accessGuard = accessGuard;
        this.accessService = accessService;
        this.projectService = projectService;
        this.mapper = mapper;
    }

    @Transactional
    public EvaluationProfileResponse create(UUID actorId, UUID organizationId, UUID projectId,
                                            CreateEvaluationProfileRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        projectService.assertProjectExists(organizationId, projectId);

        EvaluationProfile profile = new EvaluationProfile();
        profile.setOrganizationId(organizationId);
        profile.setProjectId(projectId);
        profile.setOwnerId(actorId);
        profile.setName(request.name().trim());
        profile.setSlug(resolveSlug(projectId, request.slug(), request.name()));
        profile.setDescription(trimToNull(request.description()));
        profile.setMetrics(toMetricSpecs(request.metrics()));
        profile.setPassThreshold(request.passThreshold());

        EvaluationProfile saved = profileRepository.save(profile);
        log.info("Evaluation profile {} ('{}') created in project {} by {}",
                saved.getId(), saved.getSlug(), projectId, actorId);
        return mapper.toProfileResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationProfileResponse> list(UUID actorId, UUID organizationId, UUID projectId,
                                                        Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        return PageResponse.from(
                profileRepository.findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(projectId, pageable),
                mapper::toProfileResponse);
    }

    @Transactional(readOnly = true)
    public EvaluationProfileResponse get(UUID actorId, UUID organizationId, UUID projectId, UUID profileId) {
        return mapper.toProfileResponse(
                accessGuard.requireReadableProfile(organizationId, projectId, profileId, actorId));
    }

    @Transactional
    public EvaluationProfileResponse update(UUID actorId, UUID organizationId, UUID projectId, UUID profileId,
                                            UpdateEvaluationProfileRequest request) {
        EvaluationProfile profile = accessGuard.requireManageableProfile(organizationId, projectId, profileId,
                actorId, OrganizationRole.MEMBER);
        if (StringUtils.hasText(request.name())) {
            profile.setName(request.name().trim());
        }
        if (request.description() != null) {
            profile.setDescription(trimToNull(request.description()));
        }
        if (request.metrics() != null) {
            profile.setMetrics(toMetricSpecs(request.metrics()));
        }
        if (request.passThreshold() != null) {
            profile.setPassThreshold(request.passThreshold());
        }
        return mapper.toProfileResponse(profile);
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId, UUID profileId) {
        EvaluationProfile profile = accessGuard.requireManageableProfile(organizationId, projectId, profileId,
                actorId, OrganizationRole.ADMIN);
        profile.softDelete(actorId);
        log.info("Evaluation profile {} soft-deleted in project {} by {}", profileId, projectId, actorId);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private List<MetricSpec> toMetricSpecs(List<MetricSpecDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(this::toMetricSpec).toList();
    }

    private MetricSpec toMetricSpec(MetricSpecDto dto) {
        validateMetric(dto);
        return new MetricSpec(dto.type(), trimToNull(dto.label()), dto.weight(), dto.threshold(), dto.params());
    }

    private void validateMetric(MetricSpecDto dto) {
        switch (dto.type()) {
            case REGEX_MATCH -> {
                Object pattern = dto.params() == null ? null : dto.params().get("pattern");
                if (pattern == null || pattern.toString().isBlank()) {
                    throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                            "REGEX_MATCH requires a 'pattern' param");
                }
                try {
                    Pattern.compile(pattern.toString());
                } catch (PatternSyntaxException e) {
                    throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                            "REGEX_MATCH 'pattern' is not a valid regular expression");
                }
            }
            case LATENCY, COST, TOKEN_COUNT -> {
                if (dto.threshold() == null) {
                    throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                            dto.type() + " requires a threshold");
                }
            }
            default -> { /* no extra requirements */ }
        }
    }

    private String resolveSlug(UUID projectId, String requestedSlug, String name) {
        if (StringUtils.hasText(requestedSlug)) {
            String slug = requestedSlug.trim().toLowerCase(Locale.ROOT);
            if (profileRepository.existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(projectId, slug)) {
                throw new ResourceConflictException(ErrorCode.SLUG_ALREADY_EXISTS,
                        "An evaluation profile with this slug already exists in the project");
            }
            return slug;
        }
        return SlugGenerator.uniqueSlug(name,
                candidate -> profileRepository.existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(projectId, candidate));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
