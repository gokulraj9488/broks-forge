package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import com.broksforge.modules.model.judge.CosineSimilarity;
import com.broksforge.modules.model.judge.EmbeddingResult;
import com.broksforge.modules.model.judge.EmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Embedding cosine similarity between the output and the expected output — the conversational
 * default in place of {@code EXACT_MATCH}, since a helpful answer legitimately varies in wording.
 * Params: {@code providerId} (required, the embedding provider), {@code embeddingModel}
 * (optional, provider-specific default otherwise). Threshold defaults to 0.75 when unset.
 */
@Component
public class SemanticSimilarityMetric extends AbstractEvaluationMetric {

    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.75");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EmbeddingService embeddingService;

    public SemanticSimilarityMetric(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.SEMANTIC_SIMILARITY;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        if (ctx.expectedOutput() == null || ctx.expectedOutput().isBlank()) {
            return pass(spec, "No expected output to compare against");
        }
        UUID providerId = uuidParam(spec, "providerId");
        String embeddingModel = strParam(spec, "embeddingModel");

        EmbeddingResult outputEmbedding = embeddingService.embed(providerId, embeddingModel, nullToEmpty(ctx.output()));
        if (!outputEmbedding.ok()) {
            return executionError(spec, MetricExecutionStatus.classify(outputEmbedding.httpStatus(), outputEmbedding.error()),
                    outputEmbedding.error());
        }
        EmbeddingResult expectedEmbedding = embeddingService.embed(providerId, embeddingModel, ctx.expectedOutput());
        if (!expectedEmbedding.ok()) {
            return executionError(spec, MetricExecutionStatus.classify(expectedEmbedding.httpStatus(), expectedEmbedding.error()),
                    expectedEmbedding.error());
        }

        Double similarity = CosineSimilarity.of(outputEmbedding.vector(), expectedEmbedding.vector());
        if (similarity == null) {
            return executionError(spec, MetricExecutionStatus.INFRASTRUCTURE_ERROR,
                    "Could not compute cosine similarity (empty or mismatched embeddings)");
        }
        BigDecimal score = BigDecimal.valueOf(similarity).setScale(4, RoundingMode.HALF_UP);
        BigDecimal threshold = spec.threshold() != null ? spec.threshold() : DEFAULT_THRESHOLD;
        boolean passed = score.compareTo(threshold) >= 0;
        String detail = buildDetailJson(score, embeddingModel);
        return scoredOutcome(spec, passed, score, threshold, detail);
    }

    /**
     * A compact JSON envelope (not a human sentence) so the Runs UI renders it the same
     * structured way as the judge-family metrics — score plus the embedding model used and the
     * vector distance (1 - cosine similarity), so a low score can be diagnosed without re-running.
     */
    private String buildDetailJson(BigDecimal score, String embeddingModel) {
        try {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("score", score);
            if (embeddingModel != null && !embeddingModel.isBlank()) {
                node.put("embeddingModel", embeddingModel);
            }
            node.put("distance", BigDecimal.ONE.subtract(score).setScale(4, RoundingMode.HALF_UP));
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"score\":" + score.toPlainString() + "}";
        }
    }
}
