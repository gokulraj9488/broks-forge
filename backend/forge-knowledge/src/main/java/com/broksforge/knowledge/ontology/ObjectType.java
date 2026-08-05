package com.broksforge.knowledge.ontology;

import com.broksforge.kernel.api.Kind;

import java.util.regex.Pattern;

/**
 * A knowledge object type — the semantic identity of a class of kernel node.
 *
 * <p>An {@code ObjectType} binds a human name to a kernel {@link Kind} (its epistemic status) and a
 * reserved {@code subtype} token (its position in the catalog), plus the {@link PayloadSchema} its
 * instances must satisfy. It is pure data (KN-0001): the whole ontology is a set of these values, and
 * the framework that validates and stores instances never changes when a type is added.
 *
 * @param name    the type name (e.g. {@code "Agent"})
 * @param kind    the kernel kind it maps to (its epistemic status)
 * @param subtype the reserved kernel subtype token (e.g. {@code "agent"})
 * @param schema  the payload schema instances must satisfy
 */
public record ObjectType(String name, Kind kind, String subtype, PayloadSchema schema) {

    /** Subtypes must satisfy the kernel Revision subtype grammar. */
    private static final Pattern SUBTYPE = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    public ObjectType {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("object type name must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("object type kind must not be null");
        }
        if (subtype == null || !SUBTYPE.matcher(subtype).matches()) {
            throw new IllegalArgumentException("object subtype must match " + SUBTYPE.pattern() + ": " + subtype);
        }
        if (schema == null) {
            throw new IllegalArgumentException("object type schema must not be null");
        }
    }

    /**
     * @param name    the type name
     * @param kind    the kernel kind
     * @param subtype the reserved subtype
     * @param schema  the payload schema
     * @return the object type
     */
    public static ObjectType of(String name, Kind kind, String subtype, PayloadSchema schema) {
        return new ObjectType(name, kind, subtype, schema);
    }

    /** @return true if instances of this type are single-revision (Observation/Decision — CI-6) */
    public boolean isSingleRevision() {
        return kind == Kind.OBSERVATION || kind == Kind.DECISION;
    }
}
