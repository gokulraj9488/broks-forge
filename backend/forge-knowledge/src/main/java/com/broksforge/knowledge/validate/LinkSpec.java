package com.broksforge.knowledge.validate;

import com.broksforge.kernel.api.Verb;
import com.broksforge.knowledge.ontology.ObjectType;

/**
 * A relationship to validate at object-creation time: a verb and the object type of its target. The
 * typed façade resolves user-facing links to these specs before validation, so the validator depends
 * only on the ontology, never on the graph package.
 *
 * @param verb       the relationship verb
 * @param targetType the object type of the target node
 */
public record LinkSpec(Verb verb, ObjectType targetType) {

    public LinkSpec {
        if (verb == null || targetType == null) {
            throw new IllegalArgumentException("link verb and target type must not be null");
        }
    }
}
