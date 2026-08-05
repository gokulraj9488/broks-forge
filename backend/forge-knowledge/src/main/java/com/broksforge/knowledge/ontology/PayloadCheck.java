package com.broksforge.knowledge.ontology;

import com.broksforge.kernel.api.canonical.CanonicalValue;

import java.util.List;

/**
 * A custom payload validation hook for an object type, beyond the declarative {@link PayloadSchema}
 * (e.g. cross-field rules). Registered per object type on the {@link Ontology} and invoked by the
 * validator. Kept in the {@code ontology} package (not {@code spi}) so the dependency graph stays
 * acyclic; it is nonetheless a first-class extension point.
 */
@FunctionalInterface
public interface PayloadCheck {

    /**
     * @param payload the object's payload
     * @return a list of problem messages; empty if the payload is acceptable
     */
    List<String> check(CanonicalValue payload);
}
