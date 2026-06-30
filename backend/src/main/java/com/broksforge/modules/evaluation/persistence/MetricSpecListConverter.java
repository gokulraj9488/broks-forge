package com.broksforge.modules.evaluation.persistence;

import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists an {@link EvaluationProfile}'s ordered list of {@link MetricSpec} as a
 * JSON document in a {@code text} column. Applied explicitly via {@code @Convert}.
 */
@Converter
public class MetricSpecListConverter implements AttributeConverter<List<MetricSpec>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<MetricSpec>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<MetricSpec> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize metric specs to JSON", e);
        }
    }

    @Override
    public List<MetricSpec> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize metric specs JSON", e);
        }
    }
}
