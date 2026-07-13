package com.broksforge.modules.benchmark.web.dto;

import com.broksforge.modules.benchmark.domain.BenchmarkTemplateKey;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.MetricSpecDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class BenchmarkGalleryDtos {

    private BenchmarkGalleryDtos() {
    }

    @Schema(name = "GalleryTemplateResponse", description = "A built-in Benchmark Gallery template")
    public record GalleryTemplateResponse(
            BenchmarkTemplateKey key,
            String name,
            String description,
            String category,
            int datasetItemCount,
            List<MetricSpecDto> metrics,
            boolean requiresJudgeProvider,
            boolean requiresEmbeddingProvider
    ) {
    }

    @Schema(name = "ProvisionGalleryBenchmarkRequest",
            description = "Provisions a dataset, prompt and evaluation profile from a gallery template, then runs "
                    + "it against the given agent")
    public record ProvisionGalleryBenchmarkRequest(
            @NotNull BenchmarkTemplateKey templateKey,
            @NotNull UUID agentId,
            @Schema(description = "Required when the template's metrics need a judge model (LLM_JUDGE, "
                    + "HALLUCINATION_DETECTION, CITATION_VERIFICATION)")
            UUID judgeProviderId,
            @Size(max = 128) String judgeModel,
            @Schema(description = "Required when the template's metrics need an embedding model (SEMANTIC_SIMILARITY)")
            UUID embeddingProviderId,
            @Size(max = 128) String embeddingModel,
            @Size(max = 160) String name
    ) {
    }

    @Schema(name = "ProvisionGalleryBenchmarkResponse", description = "The entities provisioned from a gallery template")
    public record ProvisionGalleryBenchmarkResponse(
            UUID datasetId,
            UUID promptId,
            UUID profileId,
            EvaluationJobResponse job
    ) {
    }
}
