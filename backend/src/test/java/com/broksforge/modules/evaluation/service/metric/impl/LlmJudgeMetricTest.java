package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import com.broksforge.modules.model.judge.JudgeInvocationService;
import com.broksforge.modules.model.judge.JudgeVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LlmJudgeMetric (AbstractJudgeMetric)")
class LlmJudgeMetricTest {

    private final JudgeInvocationService judgeInvocationService = mock(JudgeInvocationService.class);
    private final LlmJudgeMetric metric = new LlmJudgeMetric(judgeInvocationService);
    private final UUID providerId = UUID.randomUUID();

    private MetricContext ctx() {
        return new MetricContext("What is the refund policy?", "You can request a refund within 30 days.",
                "Refunds are available within 30 days of purchase.", 200L, 40, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("a score above threshold passes, and the judge's reasoning is included in the detail")
    void scoreAboveThresholdPasses() {
        when(judgeInvocationService.judge(eq(providerId), any(), any()))
                .thenReturn(JudgeVerdict.of(new BigDecimal("0.9"), "Accurate and on-topic"));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.LLM_JUDGE, null, null, null,
                Map.of("providerId", providerId.toString()));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.score()).isEqualByComparingTo(new BigDecimal("0.9"));
        assertThat(outcome.detail()).contains("Accurate and on-topic");
    }

    @Test
    @DisplayName("a score below the default 0.7 threshold fails")
    void scoreBelowDefaultThresholdFails() {
        when(judgeInvocationService.judge(eq(providerId), any(), any()))
                .thenReturn(JudgeVerdict.of(new BigDecimal("0.4"), "Missed key details"));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.LLM_JUDGE, null, null, null,
                Map.of("providerId", providerId.toString()));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(outcome.passed()).isFalse();
    }

    @Test
    @DisplayName("a custom threshold overrides the 0.7 default")
    void customThresholdOverridesDefault() {
        when(judgeInvocationService.judge(eq(providerId), any(), any()))
                .thenReturn(JudgeVerdict.of(new BigDecimal("0.6"), "Decent"));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.LLM_JUDGE, null, null, new BigDecimal("0.5"),
                Map.of("providerId", providerId.toString()));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(outcome.passed()).isTrue();
    }

    @Test
    @DisplayName("a judge invocation error becomes an execution failure carrying the error as detail, not a low score")
    void judgeErrorBecomesExecutionFailure() {
        when(judgeInvocationService.judge(eq(providerId), any(), any()))
                .thenReturn(JudgeVerdict.error("Judge provider disabled"));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.LLM_JUDGE, null, null, null,
                Map.of("providerId", providerId.toString()));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(outcome.passed()).isNull();
        assertThat(outcome.score()).isNull();
        assertThat(outcome.executionStatus()).isEqualTo(MetricExecutionStatus.PROVIDER_UNAVAILABLE);
        assertThat(outcome.detail()).isEqualTo("Judge provider disabled");
    }

    @Test
    @DisplayName("a judge HTTP 401 classifies as an authentication failure")
    void judgeHttp401ClassifiesAsAuthenticationError() {
        when(judgeInvocationService.judge(eq(providerId), any(), any()))
                .thenReturn(JudgeVerdict.error("Judge model returned HTTP 401: invalid api key", 401));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.LLM_JUDGE, null, null, null,
                Map.of("providerId", providerId.toString()));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(outcome.executionStatus()).isEqualTo(MetricExecutionStatus.AUTHENTICATION_ERROR);
    }

    @Test
    @DisplayName("a judge HTTP 429 classifies as rate-limited")
    void judgeHttp429ClassifiesAsRateLimited() {
        when(judgeInvocationService.judge(eq(providerId), any(), any()))
                .thenReturn(JudgeVerdict.error("Judge model returned HTTP 429: too many requests", 429));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.LLM_JUDGE, null, null, null,
                Map.of("providerId", providerId.toString()));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(outcome.executionStatus()).isEqualTo(MetricExecutionStatus.RATE_LIMITED);
    }

    @Test
    @DisplayName("an explicit params.context wins over falling back to expectedOutput")
    void explicitContextParamWinsOverExpectedOutput() {
        when(judgeInvocationService.judge(any(), any(), contains("EXPLICIT CONTEXT")))
                .thenReturn(JudgeVerdict.of(BigDecimal.ONE, null));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.LLM_JUDGE, null, null, null,
                Map.of("providerId", providerId.toString(), "context", "EXPLICIT CONTEXT"));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(outcome.passed()).isTrue();
        verify(judgeInvocationService).judge(eq(providerId), isNull(), contains("EXPLICIT CONTEXT"));
    }

    @Test
    @DisplayName("detail is a compact JSON envelope carrying score, reasoning, and criteria for the Runs UI")
    void detailIsStructuredJsonEnvelope() {
        when(judgeInvocationService.judge(eq(providerId), any(), any()))
                .thenReturn(JudgeVerdict.of(new BigDecimal("0.93"), "Great response",
                        Map.of("Correctness", 9, "Helpfulness", 10)));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.LLM_JUDGE, null, null, null,
                Map.of("providerId", providerId.toString()));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(outcome.detail()).contains("\"score\":0.93");
        assertThat(outcome.detail()).contains("\"reasoning\":\"Great response\"");
        assertThat(outcome.detail()).contains("\"Correctness\":9");
    }

    @Test
    @DisplayName("a custom rubric param replaces the default rubric text in the assembled prompt")
    void customRubricReplacesDefault() {
        when(judgeInvocationService.judge(any(), any(), contains("MY CUSTOM RUBRIC")))
                .thenReturn(JudgeVerdict.of(BigDecimal.ONE, null));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.LLM_JUDGE, null, null, null,
                Map.of("providerId", providerId.toString(), "rubric", "MY CUSTOM RUBRIC"));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(outcome.passed()).isTrue();
    }
}
