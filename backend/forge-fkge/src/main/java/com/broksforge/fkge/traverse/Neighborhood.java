package com.broksforge.fkge.traverse;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.util.List;

/** The semantic neighborhood of a node out to a bounded depth, both directions, over all families. */
public record Neighborhood(NodeId center, int depth, List<GraphNode> nodes, List<GraphEdge> edges, LogPosition asOf) {

    public Neighborhood {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
