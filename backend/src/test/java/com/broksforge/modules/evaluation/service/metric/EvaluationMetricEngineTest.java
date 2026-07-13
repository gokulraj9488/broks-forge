package com.broksforge.modules.evaluation.service.metric;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EvaluationMetricEngine} used to be a single exhaustive switch over every metric's
 * scoring logic. It is now a thin registry dispatcher: these tests pin (a) dispatch actually
 * reaches the right {@link EvaluationMetric} bean, (b) the zero-config default rubric is
 * unchanged (NON_EMPTY + EXACT_MATCH-if-expected — never a network-dependent metric, so every
 * pre-existing job with no profile keeps behaving identically), and (c) a metric that throws
 * (the new judge/similarity metrics call out over the network) can't crash the run or block
 * every other metric on the same row.
 */
@DisplayName("EvaluationMetricEngine — registry dispatch")
class EvaluationMetricEngineTest {

    private static final class StubMetric implements EvaluationMetric {
        private final EvaluationMetricType type;
        private final MetricOutcome outcome;
        private final RuntimeException toThrow;

        StubMetric(EvaluationMetricType type, MetricOutcome outcome) {
            this.type = type;
            this.outcome = outcome;
            this.toThrow = null;
        }

        StubMetric(EvaluationMetricType type, RuntimeException toThrow) {
            this.type = type;
            this.outcome = null;
            this.toThrow = toThrow;
        }

        @Override
        public EvaluationMetricType type() {
            return type;
        }

        @Override
        public MetricOutcome evaluate(MetricSpec spec, MetricContext context) {
            if (toThrow != null) {
                throw toThrow;
            }
            return outcome;
        }
    }

    private MetricContext context(String output, String expectedOutput) {
        return new MetricContext("rendered input", output, expectedOutput, 100L, 50, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("dispatches a spec to the registered metric bean for its type")
    void dispatchesToRegisteredMetric() {
        MetricOutcome expected = new MetricOutcome(EvaluationMetricType.NON_EMPTY, "Non-empty", true,
                BigDecimal.ONE, null, "stub ran");
        EvaluationMetricEngine engine = new EvaluationMetricEngine(
                List.of(new StubMetric(EvaluationMetricType.NON_EMPTY, expected)));

        List<MetricOutcome> outcomes = engine.evaluate(
                List.of(new MetricSpec(EvaluationMetricType.NON_EMPTY, null, null, null, Map.of())),
                context("hi", null));

        assertThat(outcomes).containsExactly(expected);
    }

    @Test
    @DisplayName("zero-config fallback is NON_EMPTY plus EXACT_MATCH only when an expected output exists")
    void zeroConfigFallbackUnchanged() {
        MetricOutcome nonEmptyOutcome = new MetricOutcome(EvaluationMetricType.NON_EMPTY, "x", true, BigDecimal.ONE, null, null);
        MetricOutcome exactMatchOutcome = new MetricOutcome(EvaluationMetricType.EXACT_MATCH, "x", true, BigDecimal.ONE, null, null);
        EvaluationMetricEngine engine = new EvaluationMetricEngine(List.of(
                new StubMetric(EvaluationMetricType.NON_EMPTY, nonEmptyOutcome),
                new StubMetric(EvaluationMetricType.EXACT_MATCH, exactMatchOutcome)));

        List<MetricOutcome> withoutExpected = engine.evaluate(List.of(), context("hi", null));
        assertThat(withoutExpected).extracting(MetricOutcome::type).containsExactly(EvaluationMetricType.NON_EMPTY);

        List<MetricOutcome> withExpected = engine.evaluate(List.of(), context("hi", "hi"));
        assertThat(withExpected).extracting(MetricOutcome::type)
                .containsExactly(EvaluationMetricType.NON_EMPTY, EvaluationMetricType.EXACT_MATCH);
    }

    @Test
    @DisplayName("a metric that throws becomes an execution failure instead of propagating or faking a low score")
    void metricExceptionBecomesExecutionFailure() {
        EvaluationMetricEngine engine = new EvaluationMetricEngine(
                List.of(new StubMetric(EvaluationMetricType.LLM_JUDGE, new RuntimeException("judge provider unreachable"))));

        List<MetricOutcome> outcomes = engine.evaluate(
                List.of(new MetricSpec(EvaluationMetricType.LLM_JUDGE, null, null, null, Map.of())),
                context("hi", "hi"));

        assertThat(outcomes).hasSize(1);
        MetricOutcome outcome = outcomes.get(0);
        assertThat(outcome.passed()).isNull();
        assertThat(outcome.score()).isNull();
        assertThat(outcome.executionStatus()).isEqualTo(MetricExecutionStatus.INFRASTRUCTURE_ERROR);
        assertThat(outcome.detail()).contains("judge provider unreachable");
    }

    @Test
    @DisplayName("a spec whose type has no registered implementation fails clearly instead of NPE-ing")
    void unregisteredTypeFailsClearly() {
        EvaluationMetricEngine engine = new EvaluationMetricEngine(List.of());

        List<MetricOutcome> outcomes = engine.evaluate(
                List.of(new MetricSpec(EvaluationMetricType.CUSTOM, null, null, null, Map.of())),
                context("hi", null));

        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.get(0).passed()).isNull();
        assertThat(outcomes.get(0).executionStatus()).isEqualTo(MetricExecutionStatus.INFRASTRUCTURE_ERROR);
        assertThat(outcomes.get(0).detail()).contains("No metric implementation registered");
    }
}
