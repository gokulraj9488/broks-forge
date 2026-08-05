package com.broksforge.knowledge.ontology;

/**
 * One declared field in an object type's payload schema.
 *
 * @param key      the payload key (lower_snake by convention)
 * @param type     the field's canonical type
 * @param required whether the field must be present
 */
public record PayloadField(String key, FieldType type, boolean required) {

    public PayloadField {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("payload field key must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("payload field type must not be null");
        }
    }

    /**
     * @param key  the key
     * @param type the type
     * @return a required field
     */
    public static PayloadField required(String key, FieldType type) {
        return new PayloadField(key, type, true);
    }

    /**
     * @param key  the key
     * @param type the type
     * @return an optional field
     */
    public static PayloadField optional(String key, FieldType type) {
        return new PayloadField(key, type, false);
    }
}
