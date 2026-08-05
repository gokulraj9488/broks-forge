package com.broksforge.knowledge.graph;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.ontology.ObjectType;

/**
 * A typed handle to a knowledge object revision — an {@link ObjectType} bound to the kernel
 * {@link Address.Revision} and {@link Revision} that realize it. It absorbs the kernel's
 * unwrap-and-cast boilerplate and carries the semantic type so callers work in ontology terms.
 *
 * @param type     the knowledge object type
 * @param address  the kernel revision address
 * @param revision the kernel revision content
 */
public record KnowledgeObject(ObjectType type, Address.Revision address, Revision revision) {

    public KnowledgeObject {
        if (type == null || address == null || revision == null) {
            throw new IllegalArgumentException("type, address, and revision must not be null");
        }
    }

    /** @return the continuant id */
    public NodeId node() {
        return address.node();
    }

    /** @return the content-addressed revision hash */
    public RevisionHash hash() {
        return address.revision();
    }

    /** @return the payload */
    public CanonicalValue payload() {
        return revision.payload();
    }
}
