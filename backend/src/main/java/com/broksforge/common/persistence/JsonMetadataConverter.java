package com.broksforge.common.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists an open-ended {@code Map<String, Object>} of custom metadata as a JSON
 * document in a {@code text} column.
 *
 * <p>This keeps the schema stable while letting callers attach arbitrary,
 * forward-compatible metadata (e.g. new agent capabilities) without a migration.
 * It is applied explicitly via {@code @Convert}, never auto-applied, so only the
 * fields that opt in are affected.</p>
 */
@Converter
public class JsonMetadataConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize metadata to JSON", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize metadata JSON", e);
        }
    }
}
