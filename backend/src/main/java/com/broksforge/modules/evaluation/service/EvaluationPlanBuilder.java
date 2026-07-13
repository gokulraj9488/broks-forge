package com.broksforge.modules.evaluation.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.modules.agent.service.AgentInvocationService;
import com.broksforge.modules.agent.service.AgentInvocationTarget;
import com.broksforge.modules.dataset.service.DatasetRow;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationProfileVersion;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.repository.EvaluationProfileVersionRepository;
import com.broksforge.modules.model.ModelTarget;
import com.broksforge.modules.prompt.service.PromptService;
import com.broksforge.modules.prompt.web.dto.PromptVersionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Resolves an {@link EvaluationJob}'s pinned agent/prompt/profile into the inputs needed to
 * execute it — shared by the synchronous path ({@link EvaluationService#run}, which additionally
 * loads every dataset row up front) and the background runner (which pages through rows instead).
 */
@Service
public class EvaluationPlanBuilder {

    private final AgentInvocationService agentInvocationService;
    private final DatasetService datasetService;
    private final PromptService promptService;
    private final EvaluationProfileVersionRepository profileVersionRepository;

    public EvaluationPlanBuilder(AgentInvocationService agentInvocationService, DatasetService datasetService,
                                 PromptService promptService,
                                 EvaluationProfileVersionRepository profileVersionRepository) {
        this.agentInvocationService = agentInvocationService;
        this.datasetService = datasetService;
        this.promptService = promptService;
        this.profileVersionRepository = profileVersionRepository;
    }

    @Transactional(readOnly = true)
    public ExecutionContext buildContext(UUID actorId, EvaluationJob job) {
        AgentInvocationTarget target = agentInvocationService.resolveTarget(
                actorId, job.getOrganizationId(), job.getProjectId(), job.getAgentId());

        String template = null;
        if (job.getPromptId() != null) {
            PromptVersionResponse promptVersion = promptService.getVersionForExecution(
                    actorId, job.getOrganizationId(), job.getProjectId(), job.getPromptId(), job.getPromptVersionId());
            template = promptVersion.template();
        }

        List<MetricSpec> metrics = List.of();
        BigDecimal passThreshold = null;
        if (job.getProfileVersionId() != null) {
            // Loaded from the version pinned at job creation, never the profile's live metrics —
            // this is what makes a historical job immune to later edits to the profile (see
            // EvaluationProfileService's version-on-metrics-change behavior).
            EvaluationProfileVersion version = profileVersionRepository.findById(job.getProfileVersionId())
                    .orElseThrow(() -> new BadRequestException(ErrorCode.EVALUATION_PROFILE_INVALID,
                            "The job's evaluation profile version no longer exists"));
            metrics = version.getMetrics();
            passThreshold = version.getPassThreshold();
        }

        ModelTarget modelTarget = new ModelTarget(target.endpointUrl(), target.headers());
        return new ExecutionContext(job.getId(), job.getOrganizationId(), job.getProjectId(),
                job.getProvider(), job.getModel(), job.getParameters(), modelTarget, template, metrics,
                passThreshold, job.getDatasetId(), job.getDatasetVersionId());
    }

    /** Full plan for the synchronous path: resolves the context and loads every row up front. */
    @Transactional(readOnly = true)
    public EvaluationPlan buildPlan(UUID actorId, EvaluationJob job) {
        ExecutionContext context = buildContext(actorId, job);
        List<DatasetRow> items = datasetService.loadExecutionItems(
                actorId, job.getOrganizationId(), job.getProjectId(), job.getDatasetId(), job.getDatasetVersionId());
        if (items.isEmpty()) {
            throw new BadRequestException(ErrorCode.EVALUATION_CONFIG_INVALID,
                    "The pinned dataset version has no items");
        }
        return context.toPlan(items);
    }
}
