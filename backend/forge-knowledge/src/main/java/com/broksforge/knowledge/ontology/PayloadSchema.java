package com.broksforge.knowledge.ontology;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The declarative payload schema of an object type: its fields and its legal role vocabulary
 * (KN-0002 — roles are payload tags, not subtypes). Validation is performed by
 * {@code KnowledgeValidator}; this type is pure data so it can be exported (KN-0001).
 */
public record PayloadSchema(List<PayloadField> fields, Set<String> roles) {

    public PayloadSchema {
        fields = List.copyOf(fields);
        roles = Set.copyOf(roles);
    }

    /** @return an empty schema (no required fields, no role vocabulary) */
    public static PayloadSchema open() {
        return new PayloadSchema(List.of(), Set.of());
    }

    /** A small builder for readability at registration sites. */
    public static Builder builder() {
        return new Builder();
    }

    /** @return the required fields */
    public List<PayloadField> required() {
        return fields.stream().filter(PayloadField::required).toList();
    }

    /** Builder for {@link PayloadSchema}. */
    public static final class Builder {
        private final List<PayloadField> fields = new ArrayList<>();
        private final Set<String> roles = new LinkedHashSet<>();

        private Builder() {
        }

        /** @param key key @param type type @return this */
        public Builder required(String key, FieldType type) {
            fields.add(PayloadField.required(key, type));
            return this;
        }

        /** @param key key @param type type @return this */
        public Builder optional(String key, FieldType type) {
            fields.add(PayloadField.optional(key, type));
            return this;
        }

        /** @param names the legal role vocabulary for the {@code role} field @return this */
        public Builder roles(String... names) {
            for (String n : names) {
                roles.add(n);
            }
            if (roles.stream().findAny().isPresent()) {
                fields.add(PayloadField.optional("role", FieldType.STRING));
            }
            return this;
        }

        /** @return the built schema */
        public PayloadSchema build() {
            return new PayloadSchema(fields, roles);
        }
    }
}
