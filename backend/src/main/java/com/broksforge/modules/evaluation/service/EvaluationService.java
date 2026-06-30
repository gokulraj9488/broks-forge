package com.broksforge.modules.evaluation.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.web.PageResponse;
import com.broksforge.config.properties.EvaluationProperties;
import com.broksforge.modules.agent.service.AgentInvocationService;
import com.broksforge.modules.agent.service.AgentInvocationTarget;
import com.broksforge.modules.dataset.service.DatasetRow;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.dataset.service.DatasetVersionRef;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationProfile;
import com.broksforge.modules.evaluation.domain.EvaluationRun;
import com.broksforge.modules.evaluation.domain.EvaluationRunStatus;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.domain.EvaluationTargetType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
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
import com.broksforge.modules.model.ModelTarget;
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
    private final EvaluationAccessGuard accessGuard;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final AgentInvocationService agentInvocationService;
    private final DatasetService datasetService;
    private final PromptService promptService;
    private final EvaluationJobExecutor executor;
    private final EvaluationJobLifecycle lifecycle;
    private final EvaluationMapper mapper;
    private final EvaluationProperties properties;

    public EvaluationService(EvaluationJobRepository jobRepository,
                             EvaluationRunRepository runRepository,
                             EvaluationResultRepository resultRepository,
                             EvaluationProfileRepository profileRepository,
                             EvaluationAccessGuard accessGuard,
                             OrganizationAccessService accessService,
                             ProjectService projectService,
                             AgentInvocationService agentInvocationService,
                             DatasetService datasetService,
                             PromptService promptService,
                             EvaluationJobExecutor executor,
                             EvaluationJobLifecycle lifecycle,
                             EvaluationMapper mapper,
                             EvaluationProperties properties) {
        this.jobRepository = jobRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.profileRepository = profileRepository;
        this.accessGuard = accessGuard;
        this.accessService = accessService;
        this.projectService = projectService;
        this.agentInvocationService = agentInvocationService;
        this.datasetService = datasetService;
        this.promptService = promptService;
        this.executor = executor;
        this.lifecycle = lifecycle;
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
        if (request.profileId() != null) {
            accessGuard.requireReadableProfile(organizationId, projectId, request.profileId(), actorId);
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
        job.setProvider(request.provider());
        job.setModel(trimToNull(request.model()));
        if (request.parameters() != null) {
            job.getParameters().putAll(request.parameters());
        }
        job.setTotalItems(datasetVersion.itemCount());

        EvaluationJob saved = lifecycle.save(job);
        log.info("Evaluation job {} ('{}') created in project {} by {}",
                saved.getId(), saved.getName(), projectId, actorId);

        if (Boolean.TRUE.equals(request.autoRun())) {
            return run(actorId, organizationId, projectId, saved.getId());
        }
        return mapper.toJobResponse(saved);
    }

    /**
     * Executes a PENDING job. The outbound model calls happen outside any transaction.
     */
    public EvaluationJobResponse run(UUID actorId, UUID organizationId, UUID projectId, UUID jobId) {
        EvaluationJob job = accessGuard.requireManageableJob(organizationId, projectId, jobId, actorId,
                OrganizationRole.MEMBER);
        if (!job.isRunnable()) {
            throw new ResourceConflictException(ErrorCode.EVALUATION_JOB_NOT_RUNNABLE,
                    "Evaluation job is not in a runnable (PENDING) state");
        }

        EvaluationPlan plan = buildPlan(actorId, job);
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

    // ----------------------------------------------------------------------
    // Plan building
    // ----------------------------------------------------------------------

    private EvaluationPlan buildPlan(UUID actorId, EvaluationJob job) {
        AgentInvocationTarget target = agentInvocationService.resolveTarget(
                actorId, job.getOrganizationId(), job.getProjectId(), job.getAgentId());

        List<DatasetRow> items = datasetService.loadExecutionItems(
                actorId, job.getOrganizationId(), job.getProjectId(), job.getDatasetId(), job.getDatasetVersionId());
        if (items.isEmpty()) {
            throw new BadRequestException(ErrorCode.EVALUATION_CONFIG_INVALID,
                    "The pinned dataset version has no items");
        }
        if (items.size() > properties.maxItemsPerJob()) {
            throw new BadRequestException(ErrorCode.EVALUATION_CONFIG_INVALID,
                    ("Dataset has %d rows, exceeding the synchronous limit of %d; reduce the dataset or raise "
                            + "broksforge.evaluation.max-items-per-job")
                            .formatted(items.size(), properties.maxItemsPerJob()));
        }

        String template = null;
        if (job.getPromptId() != null) {
            PromptVersionResponse promptVersion = promptService.getVersionForExecution(
                    actorId, job.getOrganizationId(), job.getProjectId(), job.getPromptId(), job.getPromptVersionId());
            template = promptVersion.template();
        }

        List<MetricSpec> metrics = List.of();
        java.math.BigDecimal passThreshold = null;
        if (job.getProfileId() != null) {
            EvaluationProfile profile = profileRepository
                    .findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(
                            job.getProfileId(), job.getProjectId(), job.getOrganizationId())
                    .orElseThrow(() -> new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                            "The job's evaluation profile no longer exists"));
            metrics = profile.getMetrics();
            passThreshold = profile.getPassThreshold();
        }

        ModelTarget modelTarget = new ModelTarget(target.endpointUrl(), target.headers());
        return new EvaluationPlan(job.getId(), job.getOrganizationId(), job.getProjectId(),
                job.getProvider(), job.getModel(), job.getParameters(), modelTarget, template, metrics,
                passThreshold, items);
    }

    private EvaluationJob reload(UUID organizationId, UUID projectId, UUID jobId) {
        return jobRepository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(jobId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Evaluation job", jobId));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
