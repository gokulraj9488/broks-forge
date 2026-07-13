package com.broksforge.modules.evaluation.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.util.SlugGenerator;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.EvaluationProfile;
import com.broksforge.modules.evaluation.domain.EvaluationProfileVersion;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.repository.EvaluationProfileRepository;
import com.broksforge.modules.evaluation.repository.EvaluationProfileVersionRepository;
import com.broksforge.modules.evaluation.web.EvaluationMapper;
import com.broksforge.modules.evaluation.web.dto.CreateEvaluationProfileRequest;
import com.broksforge.modules.evaluation.web.dto.EvaluationProfileResponse;
import com.broksforge.modules.evaluation.web.dto.MetricSpecDto;
import com.broksforge.modules.evaluation.web.dto.UpdateEvaluationProfileRequest;
import com.broksforge.modules.model.judge.ChatModelDiscoveryService;
import com.broksforge.modules.model.judge.EmbeddingModelDiscoveryService;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.UUID;

/**
 * CRUD for reusable evaluation profiles (metric rubrics), including immutable versioning: any
 * change to {@code metrics}/{@code passThreshold} (the actual scoring config) creates a new
 * {@link EvaluationProfileVersion} rather than mutating one in place — the same guarantee
 * {@code DatasetVersion}/{@code PromptVersion} give their containers — so a job that pinned a
 * version at creation time is immune to later edits (see {@code EvaluationJob.profileVersionId}
 * and {@code EvaluationPlanBuilder}). Plain metadata (name/slug/description) is edited in place,
 * same as Dataset/Prompt.
 */
@Slf4j
@Service
public class EvaluationProfileService {

    private final EvaluationProfileRepository profileRepository;
    private final EvaluationProfileVersionRepository versionRepository;
    private final EvaluationAccessGuard accessGuard;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final EvaluationMapper mapper;
    private final ProviderRepository providerRepository;
    private final EmbeddingModelDiscoveryService embeddingModelDiscoveryService;
    private final ChatModelDiscoveryService chatModelDiscoveryService;

    public EvaluationProfileService(EvaluationProfileRepository profileRepository,
                                    EvaluationProfileVersionRepository versionRepository,
                                    EvaluationAccessGuard accessGuard,
                                    OrganizationAccessService accessService,
                                    ProjectService projectService,
                                    EvaluationMapper mapper,
                                    ProviderRepository providerRepository,
                                    EmbeddingModelDiscoveryService embeddingModelDiscoveryService,
                                    ChatModelDiscoveryService chatModelDiscoveryService) {
        this.profileRepository = profileRepository;
        this.versionRepository = versionRepository;
        this.accessGuard = accessGuard;
        this.accessService = accessService;
        this.projectService = projectService;
        this.mapper = mapper;
        this.providerRepository = providerRepository;
        this.embeddingModelDiscoveryService = embeddingModelDiscoveryService;
        this.chatModelDiscoveryService = chatModelDiscoveryService;
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
        // Lenient: a freshly created profile (e.g. from a preset) may still have judge-family
        // metrics with no providerId yet — the user configures those in the Profile Editor next.
        // Full validation only applies when the configured profile is actually saved (see update()).
        profile.setMetrics(toMetricSpecs(request.metrics(), false, organizationId, projectId));
        profile.setPassThreshold(request.passThreshold());

        EvaluationProfile saved = profileRepository.save(profile);
        createVersion(saved);
        log.info("Evaluation profile {} ('{}') created in project {} by {}",
                saved.getId(), saved.getSlug(), projectId, actorId);
        return mapper.toProfileResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationProfileResponse> list(UUID actorId, UUID organizationId, UUID projectId,
                                                        String search, Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        String normalizedSearch = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;
        return PageResponse.from(
                profileRepository.search(projectId, normalizedSearch, pageable),
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

        boolean metricsChanged = request.metrics() != null;
        boolean thresholdChanged = request.passThreshold() != null
                && !request.passThreshold().equals(profile.getPassThreshold());
        if (metricsChanged || thresholdChanged) {
            // Strict: this is the configured profile actually being saved — judge-family metrics
            // must have a providerId by now, and that provider must actually be reachable and
            // support the metric's required capability (embeddings for Semantic Similarity, chat
            // completions for the judge-family metrics).
            List<MetricSpec> newMetrics = metricsChanged
                    ? toMetricSpecs(request.metrics(), true, organizationId, projectId) : profile.getMetrics();
            BigDecimal newThreshold = request.passThreshold() != null ? request.passThreshold() : profile.getPassThreshold();
            boolean actuallyDifferent = !Objects.equals(newMetrics, profile.getMetrics())
                    || !Objects.equals(newThreshold, profile.getPassThreshold());
            profile.setMetrics(newMetrics);
            profile.setPassThreshold(newThreshold);
            if (actuallyDifferent) {
                createVersion(profile);
                log.info("Evaluation profile {} metrics changed in project {} by {} — created version {}",
                        profileId, projectId, actorId, profile.getLatestVersionNumber());
            }
        }
        return mapper.toProfileResponse(profile);
    }

    @Transactional
    public EvaluationProfileResponse duplicate(UUID actorId, UUID organizationId, UUID projectId, UUID profileId) {
        EvaluationProfile source = accessGuard.requireManageableProfile(organizationId, projectId, profileId,
                actorId, OrganizationRole.MEMBER);
        EvaluationProfileVersion sourceVersion = currentVersionOrThrow(source);

        EvaluationProfile copy = new EvaluationProfile();
        copy.setOrganizationId(organizationId);
        copy.setProjectId(projectId);
        copy.setOwnerId(actorId);
        copy.setName(source.getName() + " (copy)");
        copy.setSlug(SlugGenerator.uniqueSlug(copy.getName(),
                candidate -> profileRepository.existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(projectId, candidate)));
        copy.setDescription(source.getDescription());
        copy.setMetrics(sourceVersion.getMetrics());
        copy.setPassThreshold(sourceVersion.getPassThreshold());
        copy.setEnabled(true);

        EvaluationProfile saved = profileRepository.save(copy);
        createVersion(saved);
        log.info("Evaluation profile {} duplicated to {} ('{}') in project {} by {}",
                profileId, saved.getId(), saved.getName(), projectId, actorId);
        return mapper.toProfileResponse(saved);
    }

    @Transactional
    public EvaluationProfileResponse setEnabled(UUID actorId, UUID organizationId, UUID projectId, UUID profileId,
                                                boolean enabled) {
        EvaluationProfile profile = accessGuard.requireManageableProfile(organizationId, projectId, profileId,
                actorId, OrganizationRole.MEMBER);
        profile.setEnabled(enabled);
        log.info("Evaluation profile {} {} in project {} by {}", profileId, enabled ? "enabled" : "disabled",
                projectId, actorId);
        return mapper.toProfileResponse(profile);
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId, UUID profileId) {
        EvaluationProfile profile = accessGuard.requireManageableProfile(organizationId, projectId, profileId,
                actorId, OrganizationRole.ADMIN);
        profile.softDelete(actorId);
        log.info("Evaluation profile {} soft-deleted in project {} by {}", profileId, projectId, actorId);
    }

    /**
     * Resolves the version a new job should pin: the explicitly requested one if given, else the
     * profile's current version. Mirrors {@code PromptService#getVersionForExecution}. Returns the
     * domain entity directly (not a DTO) since this is an internal cross-service call within the
     * evaluation module, exactly like {@code EvaluationPlanBuilder}'s existing direct repository use.
     */
    @Transactional(readOnly = true)
    public EvaluationProfileVersion getVersionForExecution(UUID actorId, UUID organizationId, UUID projectId,
                                                           UUID profileId, UUID versionId) {
        EvaluationProfile profile = accessGuard.requireReadableProfile(organizationId, projectId, profileId, actorId);
        UUID effectiveId = versionId != null ? versionId : profile.getCurrentVersionId();
        if (effectiveId == null) {
            throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                    "This evaluation profile has no versions yet");
        }
        return versionRepository.findByIdAndProfileId(effectiveId, profileId)
                .orElseThrow(() -> new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                        "Evaluation profile version not found"));
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private EvaluationProfileVersion createVersion(EvaluationProfile profile) {
        int nextNumber = profile.getLatestVersionNumber() + 1;
        EvaluationProfileVersion version = new EvaluationProfileVersion();
        version.setProfileId(profile.getId());
        version.setOrganizationId(profile.getOrganizationId());
        version.setProjectId(profile.getProjectId());
        version.setVersionNumber(nextNumber);
        version.setMetrics(profile.getMetrics());
        version.setPassThreshold(profile.getPassThreshold());

        EvaluationProfileVersion saved = versionRepository.save(version);
        profile.setLatestVersionNumber(nextNumber);
        profile.setCurrentVersionId(saved.getId());
        return saved;
    }

    private EvaluationProfileVersion currentVersionOrThrow(EvaluationProfile profile) {
        if (profile.getCurrentVersionId() == null) {
            throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                    "This evaluation profile has no versions yet");
        }
        return versionRepository.findByIdAndProfileId(profile.getCurrentVersionId(), profile.getId())
                .orElseThrow(() -> new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                        "Evaluation profile version not found"));
    }

    private List<MetricSpec> toMetricSpecs(List<MetricSpecDto> dtos, boolean strict, UUID organizationId,
                                           UUID projectId) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(dto -> toMetricSpec(dto, strict, organizationId, projectId)).toList();
    }

    private MetricSpec toMetricSpec(MetricSpecDto dto, boolean strict, UUID organizationId, UUID projectId) {
        validateMetric(dto, strict, organizationId, projectId);
        return new MetricSpec(dto.type(), trimToNull(dto.label()), dto.weight(), dto.threshold(), dto.params());
    }

    /**
     * Validates a metric's params. {@code strict} governs both the judge-family {@code providerId}
     * requirement AND the provider/model/capability checks below: false on {@link #create}, so a
     * fresh profile (e.g. from a preset picker, which necessarily creates SEMANTIC_SIMILARITY/
     * LLM_JUDGE rows with no provider chosen yet) can be saved and immediately opened in the
     * Profile Editor for provider/model configuration; true on {@link #update}, since that's the
     * point the user is actually finalizing the config — the same point this validates the
     * provider is reachable, the model (if given) actually exists for it, and the provider
     * supports the metric's required capability. Every other required param (regex pattern,
     * latency/cost/token threshold, custom metric key) is always required — presets never create
     * those metric types incomplete. A param that IS present is always format-validated (e.g. a
     * well-formed UUID/regex) regardless of strictness.
     */
    private void validateMetric(MetricSpecDto dto, boolean strict, UUID organizationId, UUID projectId) {
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
            case SEMANTIC_SIMILARITY, LLM_JUDGE, HALLUCINATION_DETECTION, CITATION_VERIFICATION -> {
                Object providerId = dto.params() == null ? null : dto.params().get("providerId");
                if (providerId == null || providerId.toString().isBlank()) {
                    if (strict) {
                        throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                                dto.type() + " requires a 'providerId' param (the judge/embedding provider to call)");
                    }
                    return;
                }
                UUID parsedProviderId;
                try {
                    parsedProviderId = UUID.fromString(providerId.toString().trim());
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                            dto.type() + " 'providerId' must be a valid UUID");
                }
                if (strict) {
                    validateProviderCapability(dto, parsedProviderId, organizationId, projectId);
                }
            }
            case CUSTOM -> {
                Object key = dto.params() == null ? null : dto.params().get("key");
                if (key == null || key.toString().isBlank()) {
                    throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                            "CUSTOM requires a 'key' param naming the registered custom metric evaluator");
                }
            }
            default -> { /* no extra requirements */ }
        }
    }

    /**
     * Confirms the provider actually exists and is reachable, and — for the metric's required
     * capability — that the provider supports it (embeddings for Semantic Similarity, chat
     * completions for the judge-family metrics) and, if a specific model was configured, that the
     * model is one the provider actually offers. A provider that's simply unreachable right now
     * (network hiccup) doesn't hard-fail the save — only a definite incompatibility does, since
     * this is a live network call and shouldn't make profile saves flaky.
     */
    private void validateProviderCapability(MetricSpecDto dto, UUID providerId, UUID organizationId, UUID projectId) {
        Provider provider = providerRepository
                .findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(providerId, projectId, organizationId)
                .orElseThrow(() -> new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                        dto.type() + ": provider " + providerId + " was not found in this project"));

        boolean isEmbedding = dto.type() == EvaluationMetricType.SEMANTIC_SIMILARITY;
        String modelParamKey = isEmbedding ? "embeddingModel" : "model";
        Object modelParam = dto.params() == null ? null : dto.params().get(modelParamKey);
        String requestedModel = modelParam == null ? null : modelParam.toString().trim();

        if (isEmbedding) {
            EmbeddingModelDiscoveryService.Result result = embeddingModelDiscoveryService.listEmbeddingModels(provider);
            checkCapabilityResult(dto, provider, result.supported(), result.models(), result.message(), requestedModel);
        } else {
            ChatModelDiscoveryService.Result result = chatModelDiscoveryService.listChatModels(provider);
            checkCapabilityResult(dto, provider, result.supported(), result.models(), result.message(), requestedModel);
        }
    }

    private void checkCapabilityResult(MetricSpecDto dto, Provider provider, boolean supported, List<String> models,
                                       String failureMessage, String requestedModel) {
        if (!supported) {
            throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                    dto.type() + ": provider '" + provider.getName() + "' " + failureMessage);
        }
        if (models.isEmpty() && failureMessage != null) {
            // Discovery explicitly failed (network/parse error), not just "genuinely zero models".
            throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                    dto.type() + ": provider '" + provider.getName() + "' is unreachable — " + failureMessage);
        }
        if (requestedModel != null && !requestedModel.isBlank() && !models.isEmpty() && !models.contains(requestedModel)) {
            throw new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                    dto.type() + ": model '" + requestedModel + "' was not found for provider '" + provider.getName() + "'");
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
