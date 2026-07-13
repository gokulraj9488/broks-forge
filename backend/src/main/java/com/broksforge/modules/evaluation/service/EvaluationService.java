package com.broksforge.modules.evaluation.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.web.PageResponse;
import com.broksforge.config.properties.EvaluationProperties;
import com.broksforge.modules.agent.service.AgentInvocationService;
import com.broksforge.modules.agent.service.AgentInvocationTarget;
import com.broksforge.modules.agent.service.HealthProbePlanner;
import com.broksforge.modules.dataset.service.DatasetRow;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.dataset.service.DatasetVersionRef;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationJobEventType;
import com.broksforge.modules.evaluation.domain.EvaluationProfile;
import com.broksforge.modules.evaluation.domain.EvaluationProfileVersion;
import com.broksforge.modules.evaluation.domain.EvaluationRun;
import com.broksforge.modules.evaluation.domain.EvaluationRunStatus;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.domain.EvaluationTargetType;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.evaluation.repository.EvaluationJobSpecifications;
import com.broksforge.modules.evaluation.repository.EvaluationProfileRepository;
import com.broksforge.modules.evaluation.repository.EvaluationResultRepository;
import com.broksforge.modules.evaluation.repository.EvaluationRunRepository;
import com.broksforge.modules.evaluation.web.EvaluationMapper;
import com.broksforge.modules.evaluation.web.dto.CreateEvaluationJobRequest;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobFilter;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobSummaryResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationResultResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationRunResponse;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import com.broksforge.modules.prompt.service.PromptService;
import com.broksforge.modules.prompt.web.dto.PromptVersionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the evaluation job lifecycle (see ADR 0005). Reference resolution and
 * status transitions happen in short transactions; the actual execution (network
 * calls + scoring) runs in {@link EvaluationJobExecutor} <em>without</em> a
 * surrounding transaction, so no database connection is held across model calls.
 */
@Slf4j
@Service
public class EvaluationService {

    private final EvaluationJobRepository jobRepository;
    private final EvaluationRunRepository runRepository;
    private final EvaluationResultRepository resultRepository;
    private final EvaluationProfileRepository profileRepository;
    private final EvaluationProfileService profileService;
    private final EvaluationAccessGuard accessGuard;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final AgentInvocationService agentInvocationService;
    private final DatasetService datasetService;
    private final PromptService promptService;
    private final EvaluationJobExecutor executor;
    private final EvaluationJobLifecycle lifecycle;
    private final EvaluationPlanBuilder planBuilder;
    private final EvaluationBackgroundRunner backgroundRunner;
    private final EvaluationJobEventService eventService;
    private final EvaluationMapper mapper;
    private final EvaluationProperties properties;

    public EvaluationService(EvaluationJobRepository jobRepository,
                             EvaluationRunRepository runRepository,
                             EvaluationResultRepository resultRepository,
                             EvaluationProfileRepository profileRepository,
                             EvaluationProfileService profileService,
                             EvaluationAccessGuard accessGuard,
                             OrganizationAccessService accessService,
                             ProjectService projectService,
                             AgentInvocationService agentInvocationService,
                             DatasetService datasetService,
                             PromptService promptService,
                             EvaluationJobExecutor executor,
                             EvaluationJobLifecycle lifecycle,
                             EvaluationPlanBuilder planBuilder,
                             EvaluationBackgroundRunner backgroundRunner,
                             EvaluationJobEventService eventService,
                             EvaluationMapper mapper,
                             EvaluationProperties properties) {
        this.jobRepository = jobRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.accessGuard = accessGuard;
        this.accessService = accessService;
        this.projectService = projectService;
        this.agentInvocationService = agentInvocationService;
        this.datasetService = datasetService;
        this.promptService = promptService;
        this.executor = executor;
        this.lifecycle = lifecycle;
        this.planBuilder = planBuilder;
        this.backgroundRunner = backgroundRunner;
        this.eventService = eventService;
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * Creates an evaluation job (PENDING), pinning the dataset/prompt versions. When
     * {@code autoRun} is set the job is executed immediately.
     */
    public EvaluationJobResponse create(UUID actorId, UUID organizationId, UUID projectId,
                                        CreateEvaluationJobRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        projectService.assertProjectExists(organizationId, projectId);

        AgentInvocationTarget target =
                agentInvocationService.resolveTarget(actorId, organizationId, projectId, request.agentId());
        DatasetVersionRef datasetVersion = datasetService.resolveVersionForExecution(
                actorId, organizationId, projectId, request.datasetId(), request.datasetVersionId());

        UUID promptVersionId = null;
        if (request.promptId() != null) {
            PromptVersionResponse promptVersion = promptService.getVersionForExecution(
                    actorId, organizationId, projectId, request.promptId(), request.promptVersionId());
            promptVersionId = promptVersion.id();
        }
        EvaluationProfileVersion profileVersion = null;
        if (request.profileId() != null) {
            profileVersion = profileService.getVersionForExecution(
                    actorId, organizationId, projectId, request.profileId(), null);
        }

        // Resolution precedence: (1) the job's own override, (2) the agent's active version's
        // model, (3) the linked provider's default model. A hosted-provider endpoint (OpenAI-
        // compatible /chat/completions, Anthropic /messages, Ollama's native /api/chat) requires
        // one in the wire request, so an unresolvable model fails validation now rather than
        // surfacing as a provider HTTP 400 partway through the run. A plain CUSTOM_REST wrapper
        // agent never needs one — it decides its own model — so this never blocks a job that
        // doesn't require it.
        String effectiveModel = trimToNull(request.model()) != null ? trimToNull(request.model()) : target.fallbackModel();
        if (HealthProbePlanner.requiresModelField(target.endpointUrl())) {
            if (effectiveModel == null) {
                throw new BadRequestException(ErrorCode.EVALUATION_CONFIG_INVALID,
                        "This agent's endpoint requires a model, and none is set. Set a model on this "
                                + "evaluation job, on the agent's active version, or as the linked provider's "
                                + "default model.");
            }
            if (HealthProbePlanner.looksLikeProviderName(effectiveModel)) {
                throw new BadRequestException(ErrorCode.EVALUATION_CONFIG_INVALID,
                        "Model must be a provider model identifier (for example: llama-3.3-70b-versatile, "
                                + "gemini-2.5-flash, gpt-4.1, claude-sonnet-4), not the provider name ('"
                                + effectiveModel + "').");
            }
        }

        EvaluationJob job = new EvaluationJob();
        job.setOrganizationId(organizationId);
        job.setProjectId(projectId);
        job.setOwnerId(actorId);
        job.setName(request.name().trim());
        job.setStatus(EvaluationStatus.PENDING);
        job.setTargetType(EvaluationTargetType.AGENT);
        job.setAgentId(request.agentId());
        job.setAgentVersionId(request.agentVersionId() != null ? request.agentVersionId() : target.activeVersionId());
        job.setDatasetId(request.datasetId());
        job.setDatasetVersionId(datasetVersion.versionId());
        job.setPromptId(request.promptId());
        job.setPromptVersionId(promptVersionId);
        job.setProfileId(request.profileId());
        if (profileVersion != null) {
            job.setProfileVersionId(profileVersion.getId());
            job.setProfileVersionNumber(profileVersion.getVersionNumber());
        }
        job.setProvider(request.provider());
        job.setModel(effectiveModel);
        if (request.parameters() != null) {
            job.getParameters().putAll(request.parameters());
        }
        job.setTotalItems(datasetVersion.itemCount());

        EvaluationJob saved = lifecycle.save(job);
        log.info("Evaluation job {} ('{}') created in project {} by {}",
                saved.getId(), saved.getName(), projectId, actorId);

        if (Boolean.TRUE.equals(request.autoRun())) {
            try {
                return run(actorId, organizationId, projectId, saved.getId());
            } catch (RuntimeException e) {
                // Auto-run failed before anything executed (e.g. a validation error) — remove the
                // job rather than leave an orphaned PENDING row the user never asked to create.
                log.warn("Auto-run failed for evaluation job {}; discarding it (create+run is atomic)",
                        saved.getId(), e);
                lifecycle.discardUnrun(saved.getId());
                throw e;
            }
        }
        return mapper.toJobResponse(saved);
    }

    /**
     * Executes a PENDING job. Datasets at or below {@code max-items-per-job} run synchronously on
     * this thread (unchanged Phase 3 behaviour: the response reflects the terminal state). Larger
     * datasets are executed in the background — this call returns immediately with the job in
     * RUNNING state; poll {@link #get} for progress (see {@code EvaluationJobResponse}'s
     * completedItems/failedItems/totalItems) until it reaches a terminal status.
     */
    public EvaluationJobResponse run(UUID actorId, UUID organizationId, UUID projectId, UUID jobId) {
        EvaluationJob job = accessGuard.requireManageableJob(organizationId, projectId, jobId, actorId,
                OrganizationRole.MEMBER);
        if (!job.isRunnable()) {
            throw new ResourceConflictException(ErrorCode.EVALUATION_JOB_NOT_RUNNABLE,
                    "Evaluation job is not in a runnable (PENDING) state");
        }

        if (job.getTotalItems() <= properties.maxItemsPerJob()) {
            EvaluationPlan plan = planBuilder.buildPlan(actorId, job);
            lifecycle.markRunning(jobId);
            try {
                EvaluationOutcome outcome = executor.execute(plan);
                lifecycle.complete(jobId, outcome);
            } catch (RuntimeException e) {
                log.error("Evaluation job {} failed during execution", jobId, e);
                lifecycle.fail(jobId, e.getMessage());
            }
            return mapper.toJobResponse(reload(organizationId, projectId, jobId));
        }

        lifecycle.queueForBackground(jobId, properties.batchSize());
        eventService.record(jobId, organizationId, EvaluationJobEventType.QUEUED,
                "Queued for background execution: %d items, batch size %d".formatted(
                        job.getTotalItems(), properties.batchSize()));
        backgroundRunner.runAsync(jobId, 1);
        return mapper.toJobResponse(reload(organizationId, projectId, jobId));
    }

    /**
     * Resumes a job that ended FAILED/CANCELLED with items still outstanding, or a RUNNING job
     * whose background pass appears stalled. Items that already succeeded are skipped — only
     * outstanding rows are (re)executed, in a new pass recorded with an incremented attempt number.
     */
    public EvaluationJobResponse resume(UUID actorId, UUID organizationId, UUID projectId, UUID jobId) {
        EvaluationJob job = accessGuard.requireManageableJob(organizationId, projectId, jobId, actorId,
                OrganizationRole.MEMBER);
        int attempt = lifecycle.resumeForBackground(jobId);
        eventService.record(jobId, organizationId, EvaluationJobEventType.RESUMED,
                "Resumed by %s (pass %d)".formatted(actorId, attempt));
        backgroundRunner.runAsync(jobId, attempt);
        return mapper.toJobResponse(reload(organizationId, projectId, jobId));
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationJobSummaryResponse> search(UUID actorId, UUID organizationId, UUID projectId,
                                                             EvaluationJobFilter filter, Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        Specification<EvaluationJob> spec = EvaluationJobSpecifications.build(
                projectId, filter.q(), filter.status(), filter.agentId(), filter.datasetId());
        return PageResponse.from(jobRepository.findAll(spec, pageable), mapper::toJobSummary);
    }

    @Transactional(readOnly = true)
    public EvaluationJobResponse get(UUID actorId, UUID organizationId, UUID projectId, UUID jobId) {
        return mapper.toJobResponse(accessGuard.requireReadableJob(organizationId, projectId, jobId, actorId));
    }

    /**
     * Published for callers that need several jobs by id in one round-trip instead of calling
     * {@link #get} per id (e.g. a benchmark leaderboard scoring each of its entries). Tenant-scoped
     * exactly like {@code get}: a ambiguous id — foreign, deleted, or simply not found — is silently
     * absent from the map rather than throwing, since the caller is resolving a best-effort batch,
     * not asserting a single resource exists.
     */
    @Transactional(readOnly = true)
    public Map<UUID, EvaluationJobResponse> getMany(UUID actorId, UUID organizationId, UUID projectId,
                                                     List<UUID> jobIds) {
        accessService.requireMembership(organizationId, actorId);
        if (jobIds == null || jobIds.isEmpty()) {
            return Map.of();
        }
        return jobRepository.findByIdInAndProjectIdAndOrganizationIdAndDeletedFalse(jobIds, projectId, organizationId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(EvaluationJob::getId, mapper::toJobResponse));
    }

    /**
     * Published for the dashboard: the most recent jobs (with summaries) in one query,
     * avoiding an N+1 when computing recent activity and per-agent roll-ups.
     */
    @Transactional(readOnly = true)
    public List<EvaluationJobResponse> recentDetailed(UUID actorId, UUID organizationId, UUID projectId) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        return jobRepository.findTop10ByProjectIdAndDeletedFalseOrderByCreatedAtDesc(projectId).stream()
                .map(mapper::toJobResponse)
                .toList();
    }

    @Transactional
    public EvaluationJobResponse cancel(UUID actorId, UUID organizationId, UUID projectId, UUID jobId) {
        EvaluationJob job = accessGuard.requireManageableJob(organizationId, projectId, jobId, actorId,
                OrganizationRole.MEMBER);
        if (job.getStatus() != EvaluationStatus.PENDING && job.getStatus() != EvaluationStatus.RUNNING) {
            throw new ResourceConflictException(ErrorCode.EVALUATION_JOB_NOT_RUNNABLE,
                    "Only pending or running jobs can be cancelled");
        }
        job.setStatus(EvaluationStatus.CANCELLED);
        log.info("Evaluation job {} cancelled in project {} by {}", jobId, projectId, actorId);
        return mapper.toJobResponse(job);
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId, UUID jobId) {
        EvaluationJob job = accessGuard.requireManageableJob(organizationId, projectId, jobId, actorId,
                OrganizationRole.ADMIN);
        job.softDelete(actorId);
        log.info("Evaluation job {} soft-deleted in project {} by {}", jobId, projectId, actorId);
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationRunResponse> listRuns(UUID actorId, UUID organizationId, UUID projectId,
                                                        UUID jobId, Pageable pageable) {
        accessGuard.requireReadableJob(organizationId, projectId, jobId, actorId);
        return PageResponse.from(
                runRepository.findByEvaluationJobIdOrderBySequenceAsc(jobId, pageable), mapper::toRunResponse);
    }

    @Transactional(readOnly = true)
    public List<EvaluationResultResponse> listResults(UUID actorId, UUID organizationId, UUID projectId,
                                                      UUID jobId, UUID runId) {
        accessGuard.requireReadableJob(organizationId, projectId, jobId, actorId);
        return resultRepository.findByEvaluationRunIdAndEvaluationJobIdOrderByMetricTypeAsc(runId, jobId).stream()
                .map(mapper::toResultResponse)
                .toList();
    }

    /** Published for the AI Debugger: a single run within a job, IDOR-safe. */
    @Transactional(readOnly = true)
    public EvaluationRunResponse getRun(UUID actorId, UUID organizationId, UUID projectId, UUID jobId, UUID runId) {
        accessGuard.requireReadableJob(organizationId, projectId, jobId, actorId);
        EvaluationRun run = runRepository.findByIdAndEvaluationJobId(runId, jobId)
                .orElseThrow(() -> ResourceNotFoundException.of("Evaluation run", runId));
        return mapper.toRunResponse(run);
    }

    /** Published for the root-cause engine: a bounded sample of failed runs in a job. */
    @Transactional(readOnly = true)
    public List<EvaluationRunResponse> sampleFailedRuns(UUID actorId, UUID organizationId, UUID projectId,
                                                        UUID jobId, int limit) {
        accessGuard.requireReadableJob(organizationId, projectId, jobId, actorId);
        int capped = Math.max(1, Math.min(limit, 200));
        return runRepository.findByEvaluationJobIdAndStatusOrderBySequenceAsc(
                        jobId, EvaluationRunStatus.FAILED, PageRequest.of(0, capped)).stream()
                .map(mapper::toRunResponse)
                .toList();
    }

    /** Published for the root-cause engine: per-metric pass/fail counts for a job. */
    @Transactional(readOnly = true)
    public List<MetricFailureTally> metricFailureBreakdown(UUID actorId, UUID organizationId, UUID projectId,
                                                           UUID jobId) {
        accessGuard.requireReadableJob(organizationId, projectId, jobId, actorId);
        return resultRepository.tallyByMetric(jobId);
    }

    /** Published for the root-cause engine: per-metric, per-status counts of metrics that never executed. */
    @Transactional(readOnly = true)
    public List<MetricExecutionFailureTally> metricExecutionFailureBreakdown(UUID actorId, UUID organizationId,
                                                                             UUID projectId, UUID jobId) {
        accessGuard.requireReadableJob(organizationId, projectId, jobId, actorId);
        return resultRepository.tallyExecutionFailuresByMetric(jobId);
    }

    private EvaluationJob reload(UUID organizationId, UUID projectId, UUID jobId) {
        return jobRepository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(jobId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Evaluation job", jobId));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
