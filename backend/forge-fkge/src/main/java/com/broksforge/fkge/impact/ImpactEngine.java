package com.broksforge.fkge.impact;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.query.Direction;
import com.broksforge.fkge.query.Lenses;
import com.broksforge.fkge.traverse.Reach;
import com.broksforge.fkge.traverse.TraversalEngine;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.NodeId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Impact = the downstream (IN) dual of provenance under {composition, derivation, evidence, intent}. Blast
 * radius, per-kind tally, downstream critical path, and closed-form influence rank — all exact, all
 * reproducible.
 */
public final class ImpactEngine {

    private final GraphIndex index;
    private final TraversalEngine traversal;
    private final Set<EdgeFamily> families = Lenses.IMPACT.families();

    public ImpactEngine(TraversalEngine traversal) {
        this.traversal = traversal;
        this.index = traversal.index();
    }

    public Impact of(NodeId n) {
        Reach r = traversal.closure(n, families, Direction.IN, -1);
        List<GraphNode> dependents = r.others();
        Map<Kind, Long> byKind = new EnumMap<>(Kind.class);
        for (GraphNode g : dependents) byKind.merge(g.kind(), 1L, Long::sum);
        List<GraphNode> critical = criticalPath(n);
        return new Impact(n, dependents, r.edges(), critical, byKind, index.position());
    }

    public Influence influence(NodeId n) {
        Impact im = of(n);
        return new Influence(n, im.radius(), Math.max(0, im.criticalPath().size() - 1), index.position());
    }

    /** Longest downstream chain from the subject (memoized DFS, total-order tie-break). Subject first. */
    public List<GraphNode> criticalPath(NodeId start) {
        return longest(start, new HashMap<>(), new LinkedHashSet<>());
    }

    private List<GraphNode> longest(NodeId cur, Map<NodeId, List<GraphNode>> memo, LinkedHashSet<NodeId> stack) {
        if (memo.containsKey(cur)) return memo.get(cur);
        if (!stack.add(cur)) return List.of();
        List<GraphNode> best = List.of();
        for (GraphEdge e : traversal.neighbors(cur, families, Direction.IN)) {
            List<GraphNode> child = longest(e.other(cur), memo, stack);
            if (child.size() > best.size()) best = child;
        }
        List<GraphNode> here = new ArrayList<>();
        index.node(cur).ifPresent(here::add);
        here.addAll(best);
        stack.remove(cur);
        List<GraphNode> result = List.copyOf(here);
        memo.put(cur, result);
        return result;
    }
}
