package com.broksforge.modules.evaluation.service;

import com.broksforge.modules.dataset.service.DatasetRow;
import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.service.metric.EvaluationMetricEngine;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import com.broksforge.modules.model.ModelInvocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link EvaluationJobExecutor#renderInput} is the sole place a dataset row's customer
 * message is merged with a prompt template before being sent to the provider. A template
 * that is pure system instructions (no {@code {{input}}} placeholder) must still carry the
 * customer's message through — otherwise the provider only ever sees static instructions
 * and answers with a generic greeting instead of addressing the actual request.
 */
@DisplayName("EvaluationJobExecutor.renderInput")
class EvaluationJobExecutorTest {

    private final EvaluationJobExecutor executor = new EvaluationJobExecutor(
            mock(ModelInvocationService.class), mock(EvaluationMetricEngine.class),
            mock(EvaluationRunProcessor.class));

    @Test
    @DisplayName("appends the dataset input when the template has no {{input}} placeholder")
    void appendsCustomerMessageWhenTemplateHasNoPlaceholder() {
        String template = "You are a helpful customer support agent. Always be polite.";
        DatasetRow row = new DatasetRow(UUID.randomUUID(), 0,
                "I need help cancelling order 12345", "Sure, I can help with that.", Map.of());

        String rendered = executor.renderInput(template, row);

        assertThat(rendered).contains(template);
        assertThat(rendered).contains("I need help cancelling order 12345");
    }

    @Test
    @DisplayName("substitutes {{input}} in place when the template references it")
    void substitutesInputPlaceholderInPlace() {
        String template = "System rules.\n\nCustomer says: {{input}}\n\nRespond helpfully.";
        DatasetRow row = new DatasetRow(UUID.randomUUID(), 0, "Where is my refund?", null, Map.of());

        String rendered = executor.renderInput(template, row);

        assertThat(rendered).isEqualTo("System rules.\n\nCustomer says: Where is my refund?\n\nRespond helpfully.");
    }

    @Test
    @DisplayName("returns the raw dataset input unchanged when there is no template at all")
    void returnsRawInputWhenNoTemplate() {
        DatasetRow row = new DatasetRow(UUID.randomUUID(), 0, "Plain customer message", null, Map.of());

        assertThat(executor.renderInput(null, row)).isEqualTo("Plain customer message");
        assertThat(executor.renderInput("  ", row)).isEqualTo("Plain customer message");
    }

    @Test
    @DisplayName("totalCost is a Number, not a String — the frontend calls .toFixed() on it directly")
    void buildSummaryEmitsTotalCostAsNumber() {
        Map<String, Object> summary = executor.buildSummary(new EvaluationJobExecutor.Accumulator());

        assertThat(summary.get("totalCost")).isInstanceOf(Number.class);
        assertThat(summary.get("totalCost")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("a metric that never executes is never averaged into avgScore or counted as a pass/fail")
    void unavailableMetricNeverContributesToAvgScoreOrEvaluationVerdict() {
        EvaluationJobExecutor.Accumulator acc = new EvaluationJobExecutor.Accumulator();
        RunTotals invocationSucceeded = new RunTotals(true, false, BigDecimal.ZERO, 100L, 50, BigDecimal.ZERO);

        // One row: NON_EMPTY passes (score 1.0), LLM_JUDGE never executes (authentication error).
        List<MetricOutcome> outcomes = List.of(
                new MetricOutcome(EvaluationMetricType.NON_EMPTY, "Non-empty", true, BigDecimal.ONE, null, null),
                new MetricOutcome(EvaluationMetricType.LLM_JUDGE, "LLM Judge", null, null, null,
                        "Judge provider rejected authentication", MetricExecutionStatus.AUTHENTICATION_ERROR));
        acc.record(invocationSucceeded, outcomes);

        Map<String, Object> summary = executor.buildSummary(acc);

        assertThat(summary.get("avgScore")).isEqualTo(1.0);
        assertThat(summary.get("completedMetricCount")).isEqualTo(1);
        assertThat(summary.get("unavailableMetricCount")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> execution = (Map<String, Object>) summary.get("execution");
        assertThat(execution.get("succeeded")).isEqualTo(1);
        assertThat(execution.get("authenticationErrors")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> breakdown = (Map<String, Object>) summary.get("metricBreakdown");
        @SuppressWarnings("unchecked")
        Map<String, Object> judgeBreakdown = (Map<String, Object>) breakdown.get("LLM_JUDGE");
        assertThat(judgeBreakdown.get("completed")).isEqualTo(0);
        assertThat(judgeBreakdown.get("total")).isEqualTo(1);
    }
}
