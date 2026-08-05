package com.broksforge.knowledge.ontology;

import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Verb;

import java.util.List;

/**
 * A relationship type — a legal edge in the ontology.
 *
 * <p>It binds a {@link Verb} (which carries the kernel {@link EdgeFamily}) to its legal source and
 * target, a {@link Cardinality}, and whether the edge is <em>intrinsic</em> (part of a revision's
 * content, hash-pinned, immutable — a kernel {@code Ref}) or <em>extrinsic</em> (asserted separately,
 * retractable — a kernel {@code AssertEdge}). Endpoints may be constrained to a specific
 * {@link ObjectType} (by name) or, more loosely, to a kernel {@link Kind}.
 *
 * @param verb        the relationship verb (and, through it, the family)
 * @param fromType    the required source object-type name, or null to match by {@code fromKind}/any
 * @param fromKind    the required source kind (if {@code fromType} is null), or null for any
 * @param toType      the required target object-type name, or null to match by {@code toTypes}/{@code toKind}
 * @param toTypes     an explicit set of allowed target type names (a bounded union, e.g. Agent|Workflow);
 *                    empty if the target is constrained by {@code toType} or {@code toKind} instead
 * @param toKind      the required target kind (if {@code toType}/{@code toTypes} are unset), or null
 * @param cardinality how many such edges a source object may/must have
 * @param intrinsic   true if this edge is an intrinsic revision ref, false if an extrinsic assertion
 */
public record RelationType(Verb verb, String fromType, Kind fromKind, String toType, List<String> toTypes,
                           Kind toKind, Cardinality cardinality, boolean intrinsic) {

    public RelationType {
        if (verb == null) {
            throw new IllegalArgumentException("relation verb must not be null");
        }
        if (cardinality == null) {
            throw new IllegalArgumentException("relation cardinality must not be null");
        }
        if (fromType == null && fromKind == null) {
            throw new IllegalArgumentException("relation must constrain its source by type or kind");
        }
        toTypes = toTypes == null ? List.of() : List.copyOf(toTypes);
        if (toType == null && toTypes.isEmpty() && toKind == null) {
            throw new IllegalArgumentException("relation must constrain its target by type(s) or kind");
        }
    }

    /** @return the edge family (from the verb) */
    public EdgeFamily family() {
        return verb.family();
    }

    /**
     * @param type a candidate source type
     * @return true if {@code type} may be the source of this relation
     */
    public boolean acceptsSource(ObjectType type) {
        return fromType != null ? fromType.equals(type.name()) : type.kind() == fromKind;
    }

    /**
     * @param type a candidate target type
     * @return true if {@code type} may be the target of this relation
     */
    public boolean acceptsTarget(ObjectType type) {
        if (toType != null) {
            return toType.equals(type.name());
        }
        if (!toTypes.isEmpty()) {
            return toTypes.contains(type.name());
        }
        return type.kind() == toKind;
    }

    /** @return a builder */
    public static Builder verb(Verb verb) {
        return new Builder(verb);
    }

    /** Fluent builder for readable registration. */
    public static final class Builder {
        private final Verb verb;
        private String fromType;
        private Kind fromKind;
        private String toType;
        private List<String> toTypes = List.of();
        private Kind toKind;
        private Cardinality cardinality = Cardinality.ANY;
        private boolean intrinsic = true;

        private Builder(Verb verb) {
            this.verb = verb;
        }

        /** @param typeName source object-type name @return this */
        public Builder from(String typeName) {
            this.fromType = typeName;
            return this;
        }

        /** @param kind source kind @return this */
        public Builder fromKind(Kind kind) {
            this.fromKind = kind;
            return this;
        }

        /** @param typeName target object-type name @return this */
        public Builder to(String typeName) {
            this.toType = typeName;
            return this;
        }

        /** @param typeNames a bounded union of allowed target type names (e.g. Agent|Workflow) @return this */
        public Builder toAny(String... typeNames) {
            this.toTypes = List.of(typeNames);
            return this;
        }

        /** @param kind target kind @return this */
        public Builder toKind(Kind kind) {
            this.toKind = kind;
            return this;
        }

        /** @param c cardinality @return this */
        public Builder card(Cardinality c) {
            this.cardinality = c;
            return this;
        }

        /** Marks this relation as extrinsic (an asserted, retractable edge). @return this */
        public Builder extrinsic() {
            this.intrinsic = false;
            return this;
        }

        /** @return the built relation type */
        public RelationType build() {
            return new RelationType(verb, fromType, fromKind, toType, toTypes, toKind, cardinality, intrinsic);
        }
    }
}
