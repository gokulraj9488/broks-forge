package com.broksforge.fkge.explain;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.query.Direction;
import com.broksforge.fkge.query.Lens;
import com.broksforge.fkge.query.Lenses;
import com.broksforge.fkge.traverse.TraversalEngine;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.canonical.CanonicalValue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds an {@link Explanation} proof tree by an upstream traversal over the licensed families, classifying
 * every leaf and reporting any gap. Deterministic (total-ordered expansion), terminating (visited set), and
 * honest (completeness is computed, not assumed).
 */
public final class ExplanationEngine {

    private final GraphIndex index;
    private final TraversalEngine traversal;

    public ExplanationEngine(TraversalEngine traversal) {
        this.traversal = traversal;
        this.index = traversal.index();
    }

    /** A general "why does this exist / hold" explanation, over the full provenance lens. */
    public Explanation explain(NodeId root) {
        return explain(root, Lenses.PROVENANCE);
    }

    /** "Why was this approved / decided" — intent then evidence. */
    public Explanation whyDecided(NodeId decision) {
        Lens lens = new Lens("why-decided", EnumSet.of(EdgeFamily.INTENT, EdgeFamily.EVIDENCE), Direction.OUT);
        return explain(decision, lens);
    }

    public Explanation explain(NodeId root, Lens lens) {
        if (index.node(root).isEmpty()) throw new IllegalArgumentException("unknown node: " + root);
        Set<EdgeFamily> families = lens.families();

        List<ExplanationStep> steps = new ArrayList<>();
        List<Explanation.Leaf> leaves = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        LinkedHashSet<NodeId> visited = new LinkedHashSet<>();

        // Depth-tracking BFS; expansion is total-ordered so the step list is stable.
        Deque<NodeId> frontier = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        frontier.add(root);
        depths.add(0);
        visited.add(root);

        while (!frontier.isEmpty()) {
            NodeId cur = frontier.poll();
            int d = depths.poll();
            List<GraphEdge> out = traversal.neighbors(cur, families, Direction.OUT);
            if (out.isEmpty()) {
                Explanation.Leaf leaf = classify(cur);
                leaves.add(leaf);
                if (leaf.kind() == LeafKind.FRONTIER) {
                    gaps.add("ungrounded " + label(cur) + " (" + cur + "): no supporting evidence/derivation reached");
                }
                continue;
            }
            for (GraphEdge e : out) {
                steps.add(new ExplanationStep(e.from(), e.verb(), e.family(), e.to(), d));
                if (visited.add(e.to())) {
                    frontier.add(e.to());
                    depths.add(d + 1);
                }
            }
        }

        boolean complete = leaves.stream().allMatch(l -> l.kind() != LeafKind.FRONTIER);
        return new Explanation(root, steps, leaves, complete, gaps, index.position());
    }

    private Explanation.Leaf classify(NodeId id) {
        GraphNode g = index.node(id).orElseThrow();
        LeafKind kind = switch (g.kind()) {
            case OBSERVATION -> LeafKind.OBSERVATION;
            case ARTIFACT -> LeafKind.PRIMARY_ARTIFACT;
            // A decision leaf is a proper axiom only if it is an explicit judgment-call. A decision that
            // rests on claims but appears as a leaf means this lens did not follow its grounding — that is a
            // gap under this lens, not a licence to certify it as an axiom.
            case DECISION -> isJudgmentCall(g) ? LeafKind.JUDGMENT_CALL : LeafKind.FRONTIER;
            case CLAIM -> LeafKind.FRONTIER; // a claim with no evidence is an ungrounded belief
        };
        return new Explanation.Leaf(id, kind);
    }

    private String label(NodeId id) {
        return index.node(id).map(GraphNode::label).orElse("node");
    }

    /** Whether a node's payload marks it an explicit judgment-call (used by callers for finer reporting). */
    public boolean isJudgmentCall(GraphNode g) {
        if (g.kind() != Kind.DECISION) return false;
        if (g.payload() instanceof CanonicalValue.Obj o) {
            CanonicalValue v = o.entries().get("judgment-call");
            return v instanceof CanonicalValue.Bool b && b.value();
        }
        return false;
    }
}
