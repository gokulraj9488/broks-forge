package com.broksforge.fkge.traverse;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.util.List;

/**
 * The result of a transitive closure: the reached nodes and the edges actually traversed, in total order,
 * plus the {@link LogPosition} the answer was computed at. {@code start} is included in {@link #nodes()}.
 */
public record Reach(NodeId start, List<GraphNode> nodes, List<GraphEdge> edges, LogPosition asOf) {

    public Reach {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** The reached nodes excluding the start — the ancestors/dependents themselves. */
    public List<GraphNode> others() {
        return nodes.stream().filter(n -> !n.id().equals(start)).toList();
    }

    public boolean reached(NodeId id) {
        return nodes.stream().anyMatch(n -> n.id().equals(id));
    }
}
