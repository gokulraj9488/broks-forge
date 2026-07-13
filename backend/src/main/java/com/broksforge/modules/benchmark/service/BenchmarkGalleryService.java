package com.broksforge.modules.benchmark.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.modules.benchmark.service.BenchmarkGalleryCatalog.GalleryTemplate;
import com.broksforge.modules.benchmark.web.dto.BenchmarkGalleryDtos.GalleryTemplateResponse;
import com.broksforge.modules.benchmark.web.dto.BenchmarkGalleryDtos.ProvisionGalleryBenchmarkRequest;
import com.broksforge.modules.benchmark.web.dto.BenchmarkGalleryDtos.ProvisionGalleryBenchmarkResponse;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.dataset.web.dto.CreateDatasetRequest;
import com.broksforge.modules.dataset.web.dto.DatasetResponse;
import com.broksforge.modules.dataset.web.dto.ImportDatasetRequest;
import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.service.EvaluationProfileService;
import com.broksforge.modules.evaluation.web.dto.CreateEvaluationJobRequest;
import com.broksforge.modules.evaluation.web.dto.CreateEvaluationProfileRequest;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationProfileResponse;
import com.broksforge.modules.evaluation.web.dto.MetricSpecDto;
import com.broksforge.modules.prompt.service.PromptService;
import com.broksforge.modules.prompt.web.dto.CreatePromptRequest;
import com.broksforge.modules.prompt.web.dto.CreatePromptVersionRequest;
import com.broksforge.modules.prompt.web.dto.PromptResponse;
import com.broksforge.modules.provider.repository.ProviderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provisions a runnable evaluation from a built-in Benchmark Gallery template: a starter
 * dataset, a prompt (with an active version), an evaluation profile with recommended metrics,
 * and an auto-run evaluation job against the caller's chosen agent — in one call, so a new
 * project has something to run instead of starting blank. Each step delegates to the same
 * service every other create flow in this codebase uses ({@link DatasetService},
 * {@link PromptService}, {@link EvaluationProfileService}, {@link EvaluationService}), so
 * provisioned entities are ordinary, fully-editable Datasets/Prompts/Profiles afterward — the
 * template is just a starting point, exactly like the frontend's evaluation-profile presets.
 */
@Slf4j
@Service
public class BenchmarkGalleryService {

    private final DatasetService datasetService;
    private final PromptService promptService;
    private final EvaluationProfileService profileService;
    private final EvaluationService evaluationService;
    private final ProviderRepository providerRepository;

    public BenchmarkGalleryService(DatasetService datasetService, PromptService promptService,
                                   EvaluationProfileService profileService, EvaluationService evaluationService,
                                   ProviderRepository providerRepository) {
        this.datasetService = datasetService;
        this.promptService = promptService;
        this.profileService = profileService;
        this.evaluationService = evaluationService;
        this.providerRepository = providerRepository;
    }

    public List<GalleryTemplateResponse> listTemplates() {
        return BenchmarkGalleryCatalog.all().stream().map(this::toResponse).toList();
    }

    /**
     * Deliberately NOT wrapped in a single {@code @Transactional}: each step below
     * ({@code DatasetService}, {@code PromptService}, {@code EvaluationProfileService},
     * {@code EvaluationService}) already commits its own transaction, exactly as if a client
     * called these five endpoints sequentially. {@code EvaluationService#create}'s auto-run
     * hands off to a background thread that reads the job row back in its own transaction —
     * if this method held one big ambient transaction across all five calls, that background
     * read would race the still-uncommitted job insert and fail with a foreign-key violation.
     */
    public ProvisionGalleryBenchmarkResponse provision(UUID actorId, UUID organizationId, UUID projectId,
                                                       ProvisionGalleryBenchmarkRequest request) {
        GalleryTemplate template = BenchmarkGalleryCatalog.byKey(request.templateKey());
        validateProviders(organizationId, projectId, template, request);

        String baseName = hasText(request.name()) ? request.name().trim() : template.name();

        DatasetResponse dataset = datasetService.create(actorId, organizationId, projectId,
                new CreateDatasetRequest(baseName + " Dataset", null,
                        "Starter dataset for the " + template.name() + " benchmark gallery template.", null, null));
        datasetService.importVersion(actorId, organizationId, projectId, dataset.id(),
                new ImportDatasetRequest(template.datasetFormat(), template.datasetContent(), null, null, null));

        PromptResponse prompt = promptService.create(actorId, organizationId, projectId,
                new CreatePromptRequest(baseName + " Prompt", null,
                        "Starter prompt for the " + template.name() + " benchmark gallery template.", null));
        promptService.createVersion(actorId, organizationId, projectId, prompt.id(),
                new CreatePromptVersionRequest(template.promptTemplate(), "Gallery template starter version",
                        null, null, true));

        List<MetricSpecDto> resolvedMetrics = resolveMetrics(template, request);
        EvaluationProfileResponse profile = profileService.create(actorId, organizationId, projectId,
                new CreateEvaluationProfileRequest(baseName + " Profile", null,
                        "Recommended metrics for the " + template.name() + " benchmark gallery template.",
                        resolvedMetrics, template.passThreshold()));

        EvaluationJobResponse job = evaluationService.create(actorId, organizationId, projectId,
                new CreateEvaluationJobRequest(baseName + " Eval", request.agentId(), null, dataset.id(), null,
                        prompt.id(), null, profile.id(), null, null, null, true));

        log.info("Benchmark Gallery template {} provisioned in project {} by {}: dataset={} prompt={} profile={} job={}",
                template.key(), projectId, actorId, dataset.id(), prompt.id(), profile.id(), job.id());
        return new ProvisionGalleryBenchmarkResponse(dataset.id(), prompt.id(), profile.id(), job);
    }

    private void validateProviders(UUID organizationId, UUID projectId, GalleryTemplate template,
                                   ProvisionGalleryBenchmarkRequest request) {
        if (template.requiresJudgeProvider()) {
            if (request.judgeProviderId() == null) {
                throw new BadRequestException(ErrorCode.BENCHMARK_CONFIG_INVALID,
                        "The " + template.name() + " template requires a judge provider for its LLM-judged metrics");
            }
            requireProvider(organizationId, projectId, request.judgeProviderId(), "judge");
        }
        if (template.requiresEmbeddingProvider()) {
            UUID embeddingProviderId = request.embeddingProviderId() != null
                    ? request.embeddingProviderId() : request.judgeProviderId();
            if (embeddingProviderId == null) {
                throw new BadRequestException(ErrorCode.BENCHMARK_CONFIG_INVALID,
                        "The " + template.name() + " template requires an embedding provider for its similarity metric");
            }
            requireProvider(organizationId, projectId, embeddingProviderId, "embedding");
        }
    }

    private void requireProvider(UUID organizationId, UUID projectId, UUID providerId, String role) {
        providerRepository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(providerId, projectId, organizationId)
                .orElseThrow(() -> new BadRequestException(ErrorCode.BENCHMARK_CONFIG_INVALID,
                        "The selected " + role + " provider was not found in this project"));
    }

    private List<MetricSpecDto> resolveMetrics(GalleryTemplate template, ProvisionGalleryBenchmarkRequest request) {
        UUID embeddingProviderId = request.embeddingProviderId() != null
                ? request.embeddingProviderId() : request.judgeProviderId();
        return template.metrics().stream().map(metric -> {
            boolean isJudgeFamily = metric.type() == EvaluationMetricType.LLM_JUDGE
                    || metric.type() == EvaluationMetricType.HALLUCINATION_DETECTION
                    || metric.type() == EvaluationMetricType.CITATION_VERIFICATION;
            boolean isEmbedding = metric.type() == EvaluationMetricType.SEMANTIC_SIMILARITY;
            if (!isJudgeFamily && !isEmbedding) {
                return metric;
            }
            Map<String, Object> params = new HashMap<>(metric.params() == null ? Map.of() : metric.params());
            if (isJudgeFamily) {
                params.put("providerId", request.judgeProviderId().toString());
                if (hasText(request.judgeModel())) {
                    params.put("model", request.judgeModel());
                }
            } else {
                params.put("providerId", embeddingProviderId.toString());
                if (hasText(request.embeddingModel())) {
                    params.put("embeddingModel", request.embeddingModel());
                }
            }
            return new MetricSpecDto(metric.type(), metric.label(), metric.weight(), metric.threshold(), params);
        }).toList();
    }

    private GalleryTemplateResponse toResponse(GalleryTemplate template) {
        return new GalleryTemplateResponse(template.key(), template.name(), template.description(),
                template.category(), template.datasetItemCount(), template.metrics(),
                template.requiresJudgeProvider(), template.requiresEmbeddingProvider(),
                template.estimatedRuntimeMinutes(), template.difficulty());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
