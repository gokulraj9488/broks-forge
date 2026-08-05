package com.broksforge.fkge.index;

import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.Verb;

/**
 * A directed, typed edge between two continuants — projected from a revision's intrinsic {@code Ref}s
 * (hash-pinned, {@code intrinsic == true}) or from a live asserted extrinsic edge ({@code intrinsic == false}).
 *
 * <p>References point from a node toward what it rests on. Its {@link EdgeFamily} fixes its meaning, so
 * traversing an edge of a chosen family is an act of inference whose conclusion cites this stored fact.
 */
public record GraphEdge(NodeId from,
                        Verb verb,
                        EdgeFamily family,
                        NodeId to,
                        boolean intrinsic,
                        LogPosition position) {

    public GraphEdge {
        if (from == null) throw new IllegalArgumentException("from");
        if (verb == null) throw new IllegalArgumentException("verb");
        if (family == null) throw new IllegalArgumentException("family");
        if (to == null) throw new IllegalArgumentException("to");
        if (position == null) throw new IllegalArgumentException("position");
    }

    /** The endpoint of this edge that is not {@code node}; used for direction-agnostic traversal. */
    public NodeId other(NodeId node) {
        return from.equals(node) ? to : from;
    }
}
