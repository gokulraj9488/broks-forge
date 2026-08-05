package com.broksforge.fkge.provenance;

import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.index.Order;
import com.broksforge.fkge.query.Direction;
import com.broksforge.fkge.query.Lenses;
import com.broksforge.fkge.traverse.Reach;
import com.broksforge.fkge.traverse.TraversalEngine;
import com.broksforge.kernel.api.NodeId;

import java.util.List;

/**
 * Provenance = the upstream closure under {composition, derivation, evidence, intent}, causally ordered,
 * certified by the subject's content-addressed revision hash. Lineage = the same restricted to derivation.
 */
public final class ProvenanceEngine {

    private final GraphIndex index;
    private final TraversalEngine traversal;

    public ProvenanceEngine(TraversalEngine traversal) {
        this.traversal = traversal;
        this.index = traversal.index();
    }

    public Provenance of(NodeId n) {
        GraphNode subject = index.node(n).orElseThrow(() -> new IllegalArgumentException("unknown node: " + n));
        Reach r = traversal.closure(n, Lenses.PROVENANCE.families(), Direction.OUT, -1);
        return new Provenance(n, r.others(), r.edges(), subject.hash(), index.position());
    }

    /** Provenance restricted to the derivation family — the exact ancestry chain of a model/dataset/prompt. */
    public List<GraphNode> lineage(NodeId n) {
        Reach r = traversal.closure(n, Lenses.LINEAGE.families(), Direction.OUT, -1);
        return r.others().stream().sorted(Order.NODES).toList();
    }
}
