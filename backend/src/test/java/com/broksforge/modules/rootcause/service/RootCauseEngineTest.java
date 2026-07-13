package com.broksforge.modules.rootcause.service;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.EvaluationRunStatus;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.domain.EvaluationTargetType;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.service.MetricExecutionFailureTally;
import com.broksforge.modules.evaluation.service.MetricFailureTally;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationRunResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RootCauseEngine#classifyRuns} used to lump every HTTP >= 400 failure into one generic
 * "the agent endpoint returned errors" finding regardless of whether it was an auth failure, a
 * quota/billing rejection, rate-limiting, an invalid model id, or a genuine provider outage — each
 * of which needs a different fix. These tests pin the distinct classification for each real
 * failure shape observed in production use of the platform.
 */
@DisplayName("RootCauseEngine — failure classification")
class RootCauseEngineTest {

    private final RootCauseEngine engine = new RootCauseEngine();

    private EvaluationJobResponse job(EvaluationStatus status) {
        return new EvaluationJobResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Job",
                UUID.randomUUID(), status, EvaluationTargetType.AGENT, UUID.randomUUID(), null,
                UUID.randomUUID(), null, null, null, null, null, null, null, "some-model", null,
                10, 0, 10, null, null, null, null, 0, null, null, null, 0, null, null);
    }

    private EvaluationRunResponse failedRun(Integer httpStatus, String error, String output) {
        return new EvaluationRunResponse(UUID.randomUUID(), 0, 1, EvaluationRunStatus.FAILED,
                UUID.randomUUID(), "input", output, 100L, null, null, null, null, httpStatus,
                false, null, error, null);
    }

    private List<RootCauseFinding> classify(EvaluationRunResponse... runs) {
        return engine.analyzeJob(job(EvaluationStatus.COMPLETED), List.of(), List.of(), List.of(runs));
    }

    private String onlyKnowledgeKey(List<RootCauseFinding> findings) {
        assertThat(findings).hasSize(1);
        return findings.get(0).knowledgeKey();
    }

    @Test
    @DisplayName("HTTP 401/403 classifies as an authentication error, not a generic HTTP error")
    void classifiesAuthenticationFailure() {
        List<RootCauseFinding> findings = classify(
                failedRun(401, "Agent endpoint returned HTTP 401: Invalid API key", null),
                failedRun(401, "Agent endpoint returned HTTP 401: Invalid API key", null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("AUTHENTICATION_ERROR");
    }

    @Test
    @DisplayName("HTTP 402 classifies as a quota/billing issue")
    void classifiesQuotaExceeded() {
        List<RootCauseFinding> findings = classify(
                failedRun(402, "Agent endpoint returned HTTP 402: requires more credits", null),
                failedRun(402, "Agent endpoint returned HTTP 402: requires more credits", null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    @DisplayName("HTTP 429 reporting a token/credit/quota limit classifies as quota, not generic rate-limit")
    void classifiesDailyQuota429AsQuota() {
        List<RootCauseFinding> findings = classify(
                failedRun(429, "Rate limit reached ... on tokens per day (TPD): Limit 100000, Used 99801", null),
                failedRun(429, "Rate limit reached ... on tokens per day (TPD): Limit 100000, Used 99801", null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    @DisplayName("a plain HTTP 429 without quota wording classifies as rate-limiting")
    void classifiesPlainRateLimit() {
        List<RootCauseFinding> findings = classify(
                failedRun(429, "Agent endpoint returned HTTP 429: too many requests", null),
                failedRun(429, "Agent endpoint returned HTTP 429: too many requests", null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("RATE_LIMIT");
    }

    @Test
    @DisplayName("HTTP 404 mentioning the model classifies as an invalid model id")
    void classifiesInvalidModel() {
        List<RootCauseFinding> findings = classify(
                failedRun(404, "Agent endpoint returned HTTP 404: The model `groq` does not exist", null),
                failedRun(404, "Agent endpoint returned HTTP 404: The model `groq` does not exist", null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("INVALID_MODEL");
    }

    @Test
    @DisplayName("HTTP 500+ classifies as a provider-side infrastructure error")
    void classifiesInfrastructureError() {
        List<RootCauseFinding> findings = classify(
                failedRun(503, "Agent endpoint returned HTTP 503: Service unavailable", null),
                failedRun(502, "Agent endpoint returned HTTP 502: Bad gateway", null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("INFRASTRUCTURE_ERROR");
    }

    @Test
    @DisplayName("a network-level failure with no HTTP status classifies distinctly from a generic error")
    void classifiesNetworkFailure() {
        List<RootCauseFinding> findings = classify(
                failedRun(null, "Connection refused: connect", null),
                failedRun(null, "Connection refused: connect", null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("NETWORK_ERROR");
    }

    @Test
    @DisplayName("a timeout still classifies as TIMEOUT (unchanged behavior)")
    void classifiesTimeout() {
        List<RootCauseFinding> findings = classify(
                failedRun(null, "Read timed out", null),
                failedRun(null, "Read timed out", null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("TIMEOUT");
    }

    @Test
    @DisplayName("blank output with no HTTP error still classifies as EMPTY_OUTPUT (unchanged behavior)")
    void classifiesEmptyOutput() {
        List<RootCauseFinding> findings = classify(
                failedRun(null, null, null),
                failedRun(null, null, ""));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("EMPTY_OUTPUT");
    }

    @Test
    @DisplayName("a generic 400 with no auth/quota/model signal still classifies as HTTP_ERROR")
    void classifiesGenericHttpError() {
        List<RootCauseFinding> findings = classify(
                failedRun(400, "Agent endpoint returned HTTP 400: 'messages' is a required property", null),
                failedRun(400, "Agent endpoint returned HTTP 400: 'messages' is a required property", null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("HTTP_ERROR");
    }

    // ------------------------------------------------------------------
    // Google AI Studio (Gemini) response shapes — GoogleAiStudioAdapter.parseError formats these
    // as "<message> (status=<GOOGLE_STATUS>)". Google's free-tier RESOURCE_EXHAUSTED message text
    // itself says "quota" ("You exceeded your current quota..."), which would otherwise trip the
    // generic quota-wording heuristic meant for OpenAI/Anthropic-style insufficient_quota errors
    // and misfile an ordinary rate limit as a billing/quota problem.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Google HTTP 429 RESOURCE_EXHAUSTED classifies as rate-limit, even though its message says \"quota\"")
    void classifiesGoogleResourceExhaustedAsRateLimit() {
        String message = "You exceeded your current quota, please check your plan and billing details. "
                + "* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, "
                + "limit: 5, model: gemini-2.5-flash. Please retry in 54s. (status=RESOURCE_EXHAUSTED)";
        List<RootCauseFinding> findings = classify(
                failedRun(429, message, null),
                failedRun(429, message, null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("RATE_LIMIT");
    }

    @Test
    @DisplayName("Google HTTP 403 reporting a quota limit classifies as quota-exceeded, not authentication")
    void classifiesGoogleQuotaExceeded403AsQuota() {
        String message = "Quota exceeded for quota metric 'Generate content API requests' (status=PERMISSION_DENIED)";
        List<RootCauseFinding> findings = classify(
                failedRun(403, message, null),
                failedRun(403, message, null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    @DisplayName("Google HTTP 403 with no quota wording still classifies as authentication, not quota")
    void classifiesGoogleAuthentication403AsAuthentication() {
        String message = "API key not valid. Please pass a valid API key. (status=PERMISSION_DENIED)";
        List<RootCauseFinding> findings = classify(
                failedRun(403, message, null),
                failedRun(403, message, null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("AUTHENTICATION_ERROR");
    }

    @Test
    @DisplayName("Google HTTP 401 classifies as authentication")
    void classifiesGoogleAuthentication401() {
        String message = "Request had invalid authentication credentials. (status=UNAUTHENTICATED)";
        List<RootCauseFinding> findings = classify(
                failedRun(401, message, null),
                failedRun(401, message, null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("AUTHENTICATION_ERROR");
    }

    @Test
    @DisplayName("Google HTTP 503 UNAVAILABLE classifies as infrastructure, never as quota")
    void classifiesGoogleUnavailable503AsInfrastructureNotQuota() {
        String message = "The model is overloaded. Please try again later. (status=UNAVAILABLE)";
        List<RootCauseFinding> findings = classify(
                failedRun(503, message, null),
                failedRun(503, message, null));
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("INFRASTRUCTURE_ERROR");
    }

    // ------------------------------------------------------------------
    // New pluggable metric types — knowledgeKeyFor/metricFinding must classify each distinctly,
    // never falling through to a generic/wrong bucket now that the metric catalogue has grown.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a dominant SEMANTIC_SIMILARITY failure rate produces its own finding")
    void classifiesSemanticSimilarityFailure() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED),
                List.of(new MetricFailureTally(EvaluationMetricType.SEMANTIC_SIMILARITY, 2L, 8L)), List.of(), List.of());
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("SEMANTIC_SIMILARITY_MISS");
    }

    @Test
    @DisplayName("a dominant LLM_JUDGE failure rate produces its own finding")
    void classifiesLlmJudgeFailure() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED),
                List.of(new MetricFailureTally(EvaluationMetricType.LLM_JUDGE, 2L, 8L)), List.of(), List.of());
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("LLM_JUDGE_LOW_SCORE");
    }

    @Test
    @DisplayName("a dominant HALLUCINATION_DETECTION failure rate produces its own finding")
    void classifiesHallucinationFailure() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED),
                List.of(new MetricFailureTally(EvaluationMetricType.HALLUCINATION_DETECTION, 2L, 8L)), List.of(), List.of());
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("HALLUCINATION_DETECTED");
    }

    @Test
    @DisplayName("a dominant CITATION_VERIFICATION failure rate produces its own finding")
    void classifiesCitationFailure() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED),
                List.of(new MetricFailureTally(EvaluationMetricType.CITATION_VERIFICATION, 2L, 8L)), List.of(), List.of());
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("CITATION_MISMATCH");
    }

    @Test
    @DisplayName("a dominant CUSTOM metric failure rate produces its own finding")
    void classifiesCustomMetricFailure() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED),
                List.of(new MetricFailureTally(EvaluationMetricType.CUSTOM, 2L, 8L)), List.of(), List.of());
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("CUSTOM_METRIC_FAILURE");
    }

    @Test
    @DisplayName("distinct failure shapes across the sample each produce their own finding")
    void mixedFailuresProduceDistinctFindings() {
        List<RootCauseFinding> findings = classify(
                failedRun(401, "Invalid API key", null),
                failedRun(429, "Rate limit reached ... tokens per day: Limit 100000", null),
                failedRun(500, "Internal server error", null));
        List<String> keys = findings.stream().map(RootCauseFinding::knowledgeKey).toList();
        assertThat(keys).containsExactlyInAnyOrder("AUTHENTICATION_ERROR", "QUOTA_EXCEEDED", "INFRASTRUCTURE_ERROR");
    }

    // ------------------------------------------------------------------
    // Metric execution failures — a judge/embedding call that never completed (auth, provider
    // outage, rate limit, unknown model, timeout) must report as the execution failure it is,
    // never as "the metric scored low" (LLM_JUDGE_LOW_SCORE / SEMANTIC_SIMILARITY_MISS).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an LLM_JUDGE authentication failure reports as a judge execution failure, not a low score")
    void judgeAuthenticationFailureReportsAsExecutionFailure() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED), List.of(),
                List.of(new MetricExecutionFailureTally(EvaluationMetricType.LLM_JUDGE,
                        MetricExecutionStatus.AUTHENTICATION_ERROR, 5L)),
                List.of());
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("JUDGE_AUTHENTICATION_ERROR");
        assertThat(findings.get(0).rootCause()).isEqualTo("Judge authentication failed");
    }

    @Test
    @DisplayName("a HALLUCINATION_DETECTION provider-unavailable failure reports as a judge execution failure")
    void judgeProviderUnavailableReportsAsExecutionFailure() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED), List.of(),
                List.of(new MetricExecutionFailureTally(EvaluationMetricType.HALLUCINATION_DETECTION,
                        MetricExecutionStatus.PROVIDER_UNAVAILABLE, 3L)),
                List.of());
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("JUDGE_PROVIDER_UNAVAILABLE");
        assertThat(findings.get(0).rootCause()).isEqualTo("Judge provider unavailable");
    }

    @Test
    @DisplayName("a SEMANTIC_SIMILARITY model-not-found failure reports as an embedding execution failure")
    void embeddingModelNotFoundReportsAsExecutionFailure() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED), List.of(),
                List.of(new MetricExecutionFailureTally(EvaluationMetricType.SEMANTIC_SIMILARITY,
                        MetricExecutionStatus.MODEL_NOT_FOUND, 4L)),
                List.of());
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("EMBEDDING_MODEL_NOT_FOUND");
        assertThat(findings.get(0).rootCause()).isEqualTo("Embedding model unavailable");
    }

    @Test
    @DisplayName("a SEMANTIC_SIMILARITY rate-limit failure reports as an embedding execution failure")
    void embeddingRateLimitedReportsAsExecutionFailure() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED), List.of(),
                List.of(new MetricExecutionFailureTally(EvaluationMetricType.SEMANTIC_SIMILARITY,
                        MetricExecutionStatus.RATE_LIMITED, 2L)),
                List.of());
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("EMBEDDING_RATE_LIMITED");
        assertThat(findings.get(0).rootCause()).isEqualTo("Embedding rate limited");
    }

    @Test
    @DisplayName("a SEMANTIC_SIMILARITY timeout reports as an embedding execution failure")
    void embeddingTimeoutReportsAsExecutionFailure() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED), List.of(),
                List.of(new MetricExecutionFailureTally(EvaluationMetricType.SEMANTIC_SIMILARITY,
                        MetricExecutionStatus.TIMEOUT, 2L)),
                List.of());
        assertThat(onlyKnowledgeKey(findings)).isEqualTo("EMBEDDING_TIMEOUT");
        assertThat(findings.get(0).rootCause()).isEqualTo("Embedding timeout");
    }

    @Test
    @DisplayName("execution failures and real low-score metrics on distinct metric types both surface, distinctly")
    void executionFailureAndLowScoreCoexistForDistinctMetrics() {
        List<RootCauseFinding> findings = engine.analyzeJob(job(EvaluationStatus.COMPLETED),
                List.of(new MetricFailureTally(EvaluationMetricType.EXACT_MATCH, 2L, 8L)),
                List.of(new MetricExecutionFailureTally(EvaluationMetricType.LLM_JUDGE,
                        MetricExecutionStatus.AUTHENTICATION_ERROR, 5L)),
                List.of());
        List<String> keys = findings.stream().map(RootCauseFinding::knowledgeKey).toList();
        assertThat(keys).containsExactlyInAnyOrder("JUDGE_AUTHENTICATION_ERROR", "EXACT_MATCH_MISS");
    }
}
