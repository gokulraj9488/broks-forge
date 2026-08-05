package com.broksforge.fkge.explain;

import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.util.List;

/**
 * A proof tree: the ordered "because" steps from a node back to its axioms. {@code complete} iff every leaf
 * is a proper axiom (observation, primary artifact, or judgment-call); otherwise {@code gaps} names each
 * unresolved frontier — an explanation is never <em>silently</em> incomplete.
 */
public record Explanation(NodeId root,
                          List<ExplanationStep> steps,
                          List<Leaf> leaves,
                          boolean complete,
                          List<String> gaps,
                          LogPosition asOf) {

    public Explanation {
        steps = List.copyOf(steps);
        leaves = List.copyOf(leaves);
        gaps = List.copyOf(gaps);
    }

    /** A classified terminus of the proof tree. */
    public record Leaf(NodeId node, LeafKind kind) {}
}
