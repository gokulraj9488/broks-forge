package com.broksforge.kernel.core.op;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Verb;

/**
 * A directed edge in the graph, as seen by traversal: a {@link Verb} from one {@link Address} to
 * another. Edges come from two sources, distinguished by {@link #intrinsic()}: intrinsic edges are
 * projected from a revision's content {@link com.broksforge.kernel.api.Ref}s (never retractable),
 * extrinsic edges are asserted separately and may be retracted.
 *
 * @param from      the source address
 * @param verb      the relationship verb (and family)
 * @param to        the target address
 * @param intrinsic true if projected from revision content, false if separately asserted
 */
public record Edge(Address from, Verb verb, Address to, boolean intrinsic) {

    public Edge {
        if (from == null || verb == null || to == null) {
            throw new IllegalArgumentException("edge from/verb/to must not be null");
        }
    }

    /** @return the edge family */
    public EdgeFamily family() {
        return verb.family();
    }
}
