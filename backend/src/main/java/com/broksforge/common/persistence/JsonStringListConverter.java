package com.broksforge.common.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists an ordered {@code List<String>} as a JSON array in a {@code text}
 * column. Used for forward-compatible, non-queried collections such as a prompt
 * template's variable names or a dataset version's column schema.
 *
 * <p>Applied explicitly via {@code @Convert} (never auto-applied). A {@code null}
 * or empty list serializes to {@code null} so absence is represented uniformly.</p>
 */
@Converter
public class JsonStringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize string list to JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize string-list JSON", e);
        }
    }
}
