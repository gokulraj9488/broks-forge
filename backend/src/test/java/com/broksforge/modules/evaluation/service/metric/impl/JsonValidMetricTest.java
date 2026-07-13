package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the original structural-validity behavior (unchanged) and the new optional
 * {@code params.schema} enforcement (additive — absent for every pre-existing profile).
 */
class JsonValidMetricTest {

    private final JsonValidMetric metric = new JsonValidMetric(new ObjectMapper());

    private MetricContext ctx(String output) {
        return new MetricContext("input", output, null, null, null, null);
    }

    private MetricSpec spec(Map<String, Object> params) {
        return new MetricSpec(EvaluationMetricType.JSON_VALID, null, null, null, params);
    }

    @Test
    @DisplayName("valid JSON with no schema configured passes (original behavior unchanged)")
    void passesValidJsonWithNoSchema() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of()), ctx("{\"a\": 1}"));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    @DisplayName("malformed JSON fails regardless of schema config")
    void failsMalformedJson() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of()), ctx("{not json"));
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.detail()).contains("not valid JSON");
    }

    @Test
    @DisplayName("empty output fails")
    void failsEmptyOutput() {
        MetricOutcome outcome = metric.evaluate(spec(Map.of()), ctx(""));
        assertThat(outcome.passed()).isFalse();
    }

    @Test
    @DisplayName("valid JSON matching the configured schema passes")
    void passesWhenSchemaMatches() {
        String schema = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";
        MetricOutcome outcome = metric.evaluate(spec(Map.of("schema", schema)), ctx("{\"name\": \"Ada\"}"));
        assertThat(outcome.passed()).isTrue();
    }

    @Test
    @DisplayName("valid JSON that violates the configured schema fails with violation detail")
    void failsWhenSchemaViolated() {
        String schema = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}";
        MetricOutcome outcome = metric.evaluate(spec(Map.of("schema", schema)), ctx("{\"age\": 5}"));
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.detail()).contains("Schema violations");
    }
}
