package com.broksforge.fkge.traverse;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.index.Order;
import com.broksforge.fkge.query.Direction;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.NodeId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The three traversal atoms every reasoning capability is built from: {@code neighbors}, {@code closure},
 * {@code path}. Each is deterministic (neighbor expansion is total-ordered), terminating (finite graph +
 * visited set), and pure (reads the immutable {@link GraphIndex}, writes nothing).
 */
public final class TraversalEngine {

    private final GraphIndex index;

    public TraversalEngine(GraphIndex index) {
        if (index == null) throw new IllegalArgumentException("index");
        this.index = index;
    }

    /** One typed step: the edges of {@code n} in {@code dir} whose family is in {@code families} (empty = all). */
    public List<GraphEdge> neighbors(NodeId n, Set<EdgeFamily> families, Direction dir) {
        List<GraphEdge> result = new ArrayList<>();
        if (dir == Direction.OUT || dir == Direction.BOTH) {
            for (GraphEdge e : index.out(n)) {
                if (families.isEmpty() || families.contains(e.family())) result.add(e);
            }
        }
        if (dir == Direction.IN || dir == Direction.BOTH) {
            for (GraphEdge e : index.in(n)) {
                if (families.isEmpty() || families.contains(e.family())) result.add(e);
            }
        }
        result.sort(Order.EDGES);
        return List.copyOf(result);
    }

    /** Transitive closure from {@code start}: BFS with a visited set; {@code maxDepth < 0} = unbounded. */
    public Reach closure(NodeId start, Set<EdgeFamily> families, Direction dir, int maxDepth) {
        LinkedHashSet<NodeId> visited = new LinkedHashSet<>();
        LinkedHashSet<GraphEdge> used = new LinkedHashSet<>();
        Map<NodeId, Integer> depth = new HashMap<>();
        Deque<NodeId> frontier = new ArrayDeque<>();

        visited.add(start);
        depth.put(start, 0);
        frontier.add(start);

        while (!frontier.isEmpty()) {
            NodeId cur = frontier.poll();
            int d = depth.get(cur);
            if (maxDepth >= 0 && d >= maxDepth) continue;
            for (GraphEdge e : neighbors(cur, families, dir)) {
                NodeId other = e.other(cur);
                used.add(e);
                if (!visited.contains(other)) {
                    visited.add(other);
                    depth.put(other, d + 1);
                    frontier.add(other);
                }
            }
        }

        List<GraphNode> nodes = new ArrayList<>();
        for (NodeId id : visited) index.node(id).ifPresent(nodes::add);
        nodes.sort(Order.NODES);
        List<GraphEdge> edges = new ArrayList<>(used);
        edges.sort(Order.EDGES);
        return new Reach(start, nodes, edges, index.position());
    }

    /**
     * A shortest connecting walk from {@code from} to {@code to} over the given families/direction, or empty
     * if none exists. Deterministic: neighbors are expanded in total order, so the discovered predecessor
     * tree — and thus the reconstructed path — is stable.
     */
    public Optional<Path> path(NodeId from, NodeId to, Set<EdgeFamily> families, Direction dir) {
        if (from.equals(to)) {
            return index.node(from).map(n -> new Path(List.of(n), List.of(), index.position()));
        }
        Map<NodeId, GraphEdge> via = new HashMap<>();
        Set<NodeId> visited = new LinkedHashSet<>();
        Deque<NodeId> frontier = new ArrayDeque<>();
        visited.add(from);
        frontier.add(from);
        boolean found = false;
        while (!frontier.isEmpty() && !found) {
            NodeId cur = frontier.poll();
            for (GraphEdge e : neighbors(cur, families, dir)) {
                NodeId other = e.other(cur);
                if (visited.contains(other)) continue;
                visited.add(other);
                via.put(other, e);
                if (other.equals(to)) {
                    found = true;
                    break;
                }
                frontier.add(other);
            }
        }
        if (!found) return Optional.empty();

        List<GraphEdge> edgesRev = new ArrayList<>();
        List<NodeId> nodeIdsRev = new ArrayList<>();
        NodeId cur = to;
        nodeIdsRev.add(cur);
        while (!cur.equals(from)) {
            GraphEdge e = via.get(cur);
            edgesRev.add(e);
            cur = e.other(cur);
            nodeIdsRev.add(cur);
        }
        List<GraphNode> nodes = new ArrayList<>();
        for (int i = nodeIdsRev.size() - 1; i >= 0; i--) {
            index.node(nodeIdsRev.get(i)).ifPresent(nodes::add);
        }
        List<GraphEdge> edges = new ArrayList<>();
        for (int i = edgesRev.size() - 1; i >= 0; i--) edges.add(edgesRev.get(i));
        return Optional.of(new Path(nodes, edges, index.position()));
    }

    /** Bounded closure in both directions over all families — the semantic neighborhood. */
    public Neighborhood neighborhood(NodeId center, int depth) {
        Reach r = closure(center, Set.of(), Direction.BOTH, depth);
        return new Neighborhood(center, depth, r.nodes(), r.edges(), index.position());
    }

    public GraphIndex index() {
        return index;
    }
}
