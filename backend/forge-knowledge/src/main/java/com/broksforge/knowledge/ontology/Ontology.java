package com.broksforge.knowledge.ontology;

import com.broksforge.kernel.api.Kind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The registry of the ontology — the object types and relationship types that make up the canonical
 * vocabulary of AI Engineering (KN-0001, the ontology is data). It is immutable once built; construction
 * runs a self-consistency check so an inconsistent ontology cannot exist.
 */
public final class Ontology {

    private final Map<String, ObjectType> byName;
    private final Map<String, ObjectType> bySubtype;
    private final List<RelationType> relations;
    private final Map<String, PayloadCheck> checks;

    private Ontology(Map<String, ObjectType> byName, Map<String, ObjectType> bySubtype,
                     List<RelationType> relations, Map<String, PayloadCheck> checks) {
        this.byName = byName;
        this.bySubtype = bySubtype;
        this.relations = relations;
        this.checks = checks;
    }

    /** @return a fresh builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return all object types, in registration order */
    public List<ObjectType> objectTypes() {
        return List.copyOf(byName.values());
    }

    /** @return all relation types, in registration order */
    public List<RelationType> relationTypes() {
        return List.copyOf(relations);
    }

    /**
     * @param name a type name
     * @return the object type, if registered
     */
    public Optional<ObjectType> type(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /**
     * Resolves the object type of a kernel node by its (kind, subtype).
     *
     * @param kind    the kernel kind
     * @param subtype the kernel subtype
     * @return the object type, if the (kind, subtype) is registered
     */
    public Optional<ObjectType> resolve(Kind kind, String subtype) {
        ObjectType t = bySubtype.get(key(kind, subtype));
        return Optional.ofNullable(t);
    }

    /**
     * @param source a source object type
     * @return the relation types whose source accepts {@code source}
     */
    public List<RelationType> relationsFrom(ObjectType source) {
        return relations.stream().filter(r -> r.acceptsSource(source)).toList();
    }

    /**
     * Finds the relation type that legalizes an edge {@code from --verb--> to}.
     *
     * @param verb the relationship verb
     * @param from the source object type
     * @param to   the target object type
     * @return the matching relation type, if the edge is legal in this ontology
     */
    public Optional<RelationType> match(com.broksforge.kernel.api.Verb verb, ObjectType from, ObjectType to) {
        return relations.stream()
                .filter(r -> r.verb().name().equals(verb.name())
                        && r.acceptsSource(from) && r.acceptsTarget(to))
                .findFirst();
    }

    /**
     * @param source a source object type
     * @return the intrinsic relation types required (min &gt; 0) from {@code source}
     */
    public List<RelationType> requiredRelationsFrom(ObjectType source) {
        return relations.stream()
                .filter(r -> r.intrinsic() && r.acceptsSource(source) && r.cardinality().min() > 0)
                .toList();
    }

    /**
     * @param typeName an object type name
     * @return the custom payload check registered for it, if any
     */
    public Optional<PayloadCheck> checkFor(String typeName) {
        return Optional.ofNullable(checks.get(typeName));
    }

    private static String key(Kind kind, String subtype) {
        return kind.wireName() + "/" + subtype;
    }

    /** Builder that validates internal consistency at {@link #build()}. */
    public static final class Builder {
        private final Map<String, ObjectType> byName = new LinkedHashMap<>();
        private final Map<String, ObjectType> bySubtype = new LinkedHashMap<>();
        private final List<RelationType> relations = new ArrayList<>();
        private final Map<String, PayloadCheck> checks = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * @param type an object type
         * @return this
         * @throws IllegalStateException if the name or (kind, subtype) is already registered
         */
        public Builder object(ObjectType type) {
            if (byName.containsKey(type.name())) {
                throw new IllegalStateException("duplicate object type name: " + type.name());
            }
            String sub = key(type.kind(), type.subtype());
            if (bySubtype.containsKey(sub)) {
                throw new IllegalStateException("duplicate (kind, subtype): " + sub
                        + " for " + type.name() + " and " + bySubtype.get(sub).name());
            }
            byName.put(type.name(), type);
            bySubtype.put(sub, type);
            return this;
        }

        /** @param relation a relation type @return this */
        public Builder relation(RelationType relation) {
            relations.add(relation);
            return this;
        }

        /** @param typeName the object type @param check a custom payload check @return this */
        public Builder check(String typeName, PayloadCheck check) {
            checks.put(typeName, check);
            return this;
        }

        /**
         * Builds the ontology after verifying: every relation endpoint named by type resolves to a
         * registered type; every verb's family matches the canonical {@link Verbs} catalog; every custom
         * check targets a registered type.
         *
         * @return the immutable, consistent ontology
         * @throws IllegalStateException on any inconsistency
         */
        public Ontology build() {
            for (RelationType r : relations) {
                if (r.fromType() != null && !byName.containsKey(r.fromType())) {
                    throw new IllegalStateException("relation '" + r.verb().name()
                            + "' references unknown source type: " + r.fromType());
                }
                if (r.toType() != null && !byName.containsKey(r.toType())) {
                    throw new IllegalStateException("relation '" + r.verb().name()
                            + "' references unknown target type: " + r.toType());
                }
                for (String tt : r.toTypes()) {
                    if (!byName.containsKey(tt)) {
                        throw new IllegalStateException("relation '" + r.verb().name()
                                + "' references unknown target type: " + tt);
                    }
                }
                Verbs.byName(r.verb().name()).ifPresent(canonical -> {
                    if (canonical.family() != r.verb().family()) {
                        throw new IllegalStateException("verb '" + r.verb().name() + "' family "
                                + r.verb().family() + " disagrees with the catalog family " + canonical.family());
                    }
                });
            }
            for (String typeName : checks.keySet()) {
                if (!byName.containsKey(typeName)) {
                    throw new IllegalStateException("custom check targets unknown type: " + typeName);
                }
            }
            return new Ontology(Map.copyOf(byName), Map.copyOf(bySubtype),
                    List.copyOf(relations), Map.copyOf(checks));
        }
    }
}
