package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import com.broksforge.modules.model.judge.EmbeddingResult;
import com.broksforge.modules.model.judge.EmbeddingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SemanticSimilarityMetric")
class SemanticSimilarityMetricTest {

    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final SemanticSimilarityMetric metric = new SemanticSimilarityMetric(embeddingService);
    private final UUID providerId = UUID.randomUUID();

    private MetricSpec spec(BigDecimal threshold) {
        return new MetricSpec(EvaluationMetricType.SEMANTIC_SIMILARITY, null, null, threshold,
                Map.of("providerId", providerId.toString()));
    }

    @Test
    @DisplayName("no expected output configured passes without calling the embedding service")
    void passesWithNoExpectedOutput() {
        MetricOutcome outcome = metric.evaluate(spec(null), new MetricContext("in", "out", null, null, null, null));
        assertThat(outcome.passed()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("identical embeddings score a perfect similarity and pass")
    void identicalEmbeddingsPass() {
        when(embeddingService.embed(eq(providerId), any(), any()))
                .thenReturn(EmbeddingResult.of(new float[]{1f, 0f, 0f}));

        MetricOutcome outcome = metric.evaluate(spec(null),
                new MetricContext("in", "the answer", "the answer", null, null, null));

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.score()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("detail is a compact JSON envelope (score + vector distance), matching the judge-family metrics' shape")
    void detailIsJsonEnvelope() {
        when(embeddingService.embed(eq(providerId), any(), any()))
                .thenReturn(EmbeddingResult.of(new float[]{1f, 0f, 0f}));

        MetricOutcome outcome = metric.evaluate(spec(null),
                new MetricContext("in", "the answer", "the answer", null, null, null));

        assertThat(outcome.detail()).isEqualTo("{\"score\":1.0000,\"distance\":0.0000}");
    }

    @Test
    @DisplayName("detail includes the embedding model when one was configured")
    void detailIncludesEmbeddingModel() {
        MetricSpec specWithModel = new MetricSpec(EvaluationMetricType.SEMANTIC_SIMILARITY, null, null, null,
                Map.of("providerId", providerId.toString(), "embeddingModel", "text-embedding-3-small"));
        when(embeddingService.embed(eq(providerId), eq("text-embedding-3-small"), any()))
                .thenReturn(EmbeddingResult.of(new float[]{1f, 0f, 0f}));

        MetricOutcome outcome = metric.evaluate(specWithModel,
                new MetricContext("in", "the answer", "the answer", null, null, null));

        assertThat(outcome.detail()).contains("\"embeddingModel\":\"text-embedding-3-small\"");
    }

    @Test
    @DisplayName("orthogonal embeddings score near zero and fail against the default threshold")
    void orthogonalEmbeddingsFail() {
        when(embeddingService.embed(eq(providerId), any(), eq("completely unrelated")))
                .thenReturn(EmbeddingResult.of(new float[]{1f, 0f}));
        when(embeddingService.embed(eq(providerId), any(), eq("reference answer")))
                .thenReturn(EmbeddingResult.of(new float[]{0f, 1f}));

        MetricOutcome outcome = metric.evaluate(spec(null),
                new MetricContext("in", "completely unrelated", "reference answer", null, null, null));

        assertThat(outcome.passed()).isFalse();
    }

    @Test
    @DisplayName("an embedding failure is an execution failure, not a low score")
    void embeddingFailureIsExecutionFailure() {
        when(embeddingService.embed(eq(providerId), any(), any()))
                .thenReturn(EmbeddingResult.error("Embedding provider disabled"));

        MetricOutcome outcome = metric.evaluate(spec(null),
                new MetricContext("in", "out", "expected", null, null, null));

        assertThat(outcome.passed()).isNull();
        assertThat(outcome.score()).isNull();
        assertThat(outcome.executionStatus()).isEqualTo(MetricExecutionStatus.PROVIDER_UNAVAILABLE);
        assertThat(outcome.detail()).contains("Embedding provider disabled");
    }

    @Test
    @DisplayName("an embedding HTTP 404 classifies as model-not-found, not a generic failure")
    void embeddingHttp404ClassifiesAsModelNotFound() {
        when(embeddingService.embed(eq(providerId), any(), any()))
                .thenReturn(EmbeddingResult.error("Embedding call returned HTTP 404: model not found", 404));

        MetricOutcome outcome = metric.evaluate(spec(null),
                new MetricContext("in", "out", "expected", null, null, null));

        assertThat(outcome.executionStatus()).isEqualTo(MetricExecutionStatus.MODEL_NOT_FOUND);
    }

    @Test
    @DisplayName("an embedding HTTP 401 classifies as an authentication failure")
    void embeddingHttp401ClassifiesAsAuthenticationError() {
        when(embeddingService.embed(eq(providerId), any(), any()))
                .thenReturn(EmbeddingResult.error("Embedding call returned HTTP 401: invalid api key", 401));

        MetricOutcome outcome = metric.evaluate(spec(null),
                new MetricContext("in", "out", "expected", null, null, null));

        assertThat(outcome.executionStatus()).isEqualTo(MetricExecutionStatus.AUTHENTICATION_ERROR);
    }

    @Test
    @DisplayName("a custom threshold is honoured over the 0.75 default")
    void customThresholdHonoured() {
        // cosine([1,1], [1,0]) ≈ 0.707 — comfortably clears the 0.75 default's absence of a
        // custom threshold but must fail once threshold is tightened to 0.99.
        when(embeddingService.embed(eq(providerId), any(), any()))
                .thenReturn(EmbeddingResult.of(new float[]{1f, 1f}), EmbeddingResult.of(new float[]{1f, 0f}));

        MetricOutcome outcome = metric.evaluate(spec(new BigDecimal("0.99")),
                new MetricContext("in", "out", "expected", null, null, null));

        assertThat(outcome.passed()).isFalse();
    }
}
