package com.broksforge.knowledge.ontology;

import com.broksforge.kernel.api.canonical.CanonicalValue;

/**
 * The declared type of a payload field, checked against the kernel's canonical value model.
 */
public enum FieldType {

    STRING, NUMBER, BOOL, OBJECT, ARRAY, ANY;

    /**
     * @param value a canonical value
     * @return true if {@code value} is an instance of this field type
     */
    public boolean matches(CanonicalValue value) {
        return switch (this) {
            case ANY -> true;
            case STRING -> value instanceof CanonicalValue.Str;
            case NUMBER -> value instanceof CanonicalValue.Num;
            case BOOL -> value instanceof CanonicalValue.Bool;
            case OBJECT -> value instanceof CanonicalValue.Obj;
            case ARRAY -> value instanceof CanonicalValue.Arr;
        };
    }
}
