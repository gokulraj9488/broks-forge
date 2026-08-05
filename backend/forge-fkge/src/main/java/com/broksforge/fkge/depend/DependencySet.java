package com.broksforge.fkge.depend;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.util.List;

/**
 * The reproduction-bearing subset of provenance: everything a node needs to be rebuilt, topologically
 * ordered (deepest dependencies first). {@code criticalPath} is the longest dependency chain — the load
 * most rests on.
 */
public record DependencySet(NodeId subject,
                            List<GraphNode> nodes,
                            List<GraphEdge> edges,
                            List<GraphNode> criticalPath,
                            LogPosition asOf) {

    public DependencySet {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        criticalPath = List.copyOf(criticalPath);
    }

    public int size() {
        return nodes.size();
    }
}
