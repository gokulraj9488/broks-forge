package com.broksforge.fkge.depend;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.index.Order;
import com.broksforge.fkge.query.Direction;
import com.broksforge.fkge.query.Lenses;
import com.broksforge.fkge.traverse.Reach;
import com.broksforge.fkge.traverse.TraversalEngine;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.NodeId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependency = upstream closure under {composition, derivation}. The intrinsic-dependency subgraph is
 * acyclic by construction (a hash-pinned ref can only target an already-existing revision), so a
 * topological order and a longest (critical) path are well-defined and deterministic.
 */
public final class DependencyEngine {

    private final GraphIndex index;
    private final TraversalEngine traversal;
    private final Set<EdgeFamily> families = Lenses.DEPENDENCY.families();

    public DependencyEngine(TraversalEngine traversal) {
        this.traversal = traversal;
        this.index = traversal.index();
    }

    public DependencySet of(NodeId n) {
        Reach r = traversal.closure(n, families, Direction.OUT, -1);
        List<GraphNode> ordered = topoOrder(n);
        List<GraphNode> critical = criticalPath(n);
        return new DependencySet(n, ordered, r.edges(), critical, index.position());
    }

    /** Post-order DFS over dependency edges → dependencies appear before the dependents that need them. */
    private List<GraphNode> topoOrder(NodeId start) {
        List<GraphNode> out = new ArrayList<>();
        LinkedHashSet<NodeId> visited = new LinkedHashSet<>();
        dfsPost(start, visited, out);
        // exclude the subject itself; it is the root, not one of its dependencies
        return out.stream().filter(g -> !g.id().equals(start)).toList();
    }

    private void dfsPost(NodeId cur, LinkedHashSet<NodeId> visited, List<GraphNode> out) {
        if (!visited.add(cur)) return;
        for (GraphEdge e : traversal.neighbors(cur, families, Direction.OUT)) {
            dfsPost(e.to(), visited, out);
        }
        index.node(cur).ifPresent(out::add);
    }

    /** Longest dependency chain from the subject to a leaf, via memoized DFS with total-order tie-breaks. */
    public List<GraphNode> criticalPath(NodeId start) {
        Map<NodeId, List<GraphNode>> memo = new HashMap<>();
        return longest(start, memo, new LinkedHashSet<>());
    }

    private List<GraphNode> longest(NodeId cur, Map<NodeId, List<GraphNode>> memo, LinkedHashSet<NodeId> stack) {
        if (memo.containsKey(cur)) return memo.get(cur);
        if (!stack.add(cur)) return List.of(); // defensive: never revisit within a path
        List<GraphNode> best = List.of();
        for (GraphEdge e : traversal.neighbors(cur, families, Direction.OUT)) {
            List<GraphNode> child = longest(e.to(), memo, stack);
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

    /** Sort a raw dependency node set by the total order (used for display alongside the topo order). */
    public List<GraphNode> ordered(Reach r) {
        List<GraphNode> nodes = new ArrayList<>(r.others());
        Collections.sort(nodes, Order.NODES);
        return List.copyOf(nodes);
    }
}
