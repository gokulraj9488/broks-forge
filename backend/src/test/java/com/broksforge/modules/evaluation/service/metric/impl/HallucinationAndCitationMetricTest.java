package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import com.broksforge.modules.model.judge.JudgeInvocationService;
import com.broksforge.modules.model.judge.JudgeVerdict;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Confirms the two judge-family metrics wire up their own type + distinct default rubric text. */
class HallucinationAndCitationMetricTest {

    private final JudgeInvocationService judgeInvocationService = mock(JudgeInvocationService.class);
    private final UUID providerId = UUID.randomUUID();

    private MetricContext ctx() {
        return new MetricContext("Summarize the source.", "The source says X, according to [1].",
                "The source discusses X and Y.", null, null, null);
    }

    @Test
    void hallucinationDetectionUsesItsOwnRubricAndType() {
        HallucinationDetectionMetric metric = new HallucinationDetectionMetric(judgeInvocationService);
        when(judgeInvocationService.judge(any(), any(), contains("hallucination")))
                .thenReturn(JudgeVerdict.of(new BigDecimal("0.9"), "Grounded"));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.HALLUCINATION_DETECTION, null, null, null,
                Map.of("providerId", providerId.toString()));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(metric.type()).isEqualTo(EvaluationMetricType.HALLUCINATION_DETECTION);
        assertThat(outcome.type()).isEqualTo(EvaluationMetricType.HALLUCINATION_DETECTION);
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void citationVerificationUsesItsOwnRubricAndType() {
        CitationVerificationMetric metric = new CitationVerificationMetric(judgeInvocationService);
        when(judgeInvocationService.judge(any(), any(), contains("citation")))
                .thenReturn(JudgeVerdict.of(new BigDecimal("0.2"), "Invented source"));

        MetricSpec spec = new MetricSpec(EvaluationMetricType.CITATION_VERIFICATION, null, null, null,
                Map.of("providerId", providerId.toString()));
        MetricOutcome outcome = metric.evaluate(spec, ctx());

        assertThat(metric.type()).isEqualTo(EvaluationMetricType.CITATION_VERIFICATION);
        assertThat(outcome.type()).isEqualTo(EvaluationMetricType.CITATION_VERIFICATION);
        assertThat(outcome.passed()).isFalse();
    }
}
