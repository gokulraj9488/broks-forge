package com.broksforge.fvcs.repo;

import com.broksforge.kernel.api.Name;
import com.broksforge.knowledge.graph.KnowledgeObject;

/**
 * A tag — an immovable named pointer to a commit, blessed by a {@code Tag} Decision. The pointer is a
 * kernel {@link Name} ({@code tag/<name>}); the act of tagging is the wrapped Decision.
 *
 * @param tag  the Tag decision knowledge object
 * @param name the immovable kernel name pointing at the tagged commit
 */
public record TagRef(KnowledgeObject tag, Name name) {

    public TagRef {
        if (tag == null || name == null) {
            throw new IllegalArgumentException("tag and name must not be null");
        }
    }
}
