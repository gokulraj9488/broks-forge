package com.broksforge.knowledge.graph;

import com.broksforge.kernel.api.Verb;

/**
 * A relationship to declare when creating a knowledge object: a verb and the target object. The
 * {@link KnowledgeGraph} resolves the matching ontology relation, validates it, and turns intrinsic
 * links into kernel {@code Ref}s inside the new revision.
 *
 * @param verb   the relationship verb (from the {@code Verbs} catalog)
 * @param target the target knowledge object
 */
public record Link(Verb verb, KnowledgeObject target) {

    public Link {
        if (verb == null || target == null) {
            throw new IllegalArgumentException("link verb and target must not be null");
        }
    }

    /**
     * @param verb   the verb
     * @param target the target object
     * @return the link
     */
    public static Link of(Verb verb, KnowledgeObject target) {
        return new Link(verb, target);
    }
}
