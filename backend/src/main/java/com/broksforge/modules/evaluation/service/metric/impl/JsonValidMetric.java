package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Output parses as well-formed JSON. Backward compatible with the original {@code JSON_VALID}
 * behaviour (structural validity only) when no schema is configured; when {@code params.schema}
 * is present (a JSON Schema document), the output is additionally validated against it — this is
 * the "JSON Schema Validation" metric from the pluggable-metric catalogue, layered onto the
 * existing type rather than a new enum constant, since renaming/duplicating would break every
 * profile and stored result that already references {@code JSON_VALID}.
 */
@Component
public class JsonValidMetric extends AbstractEvaluationMetric {

    private static final JsonSchemaFactory SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private final ObjectMapper objectMapper;

    public JsonValidMetric(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.JSON_VALID;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        String output = ctx.output();
        if (output == null || output.isBlank()) {
            return outcome(spec, false, null, "Output is empty");
        }
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(output);
        } catch (Exception e) {
            return outcome(spec, false, null, "Output is not valid JSON");
        }

        Object schemaParam = spec.paramsOrEmpty().get("schema");
        if (schemaParam == null) {
            return outcome(spec, true, null, "Output is valid JSON");
        }
        try {
            JsonNode schemaNode = schemaParam instanceof String s
                    ? objectMapper.readTree(s)
                    : objectMapper.valueToTree(schemaParam);
            JsonSchema schema = SCHEMA_FACTORY.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(parsed);
            if (errors.isEmpty()) {
                return outcome(spec, true, null, "Output is valid JSON and matches the schema");
            }
            String detail = "Schema violations: " + errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));
            return outcome(spec, false, null, detail);
        } catch (Exception e) {
            return outcome(spec, false, null, "Configured JSON schema is invalid: " + e.getMessage());
        }
    }
}
