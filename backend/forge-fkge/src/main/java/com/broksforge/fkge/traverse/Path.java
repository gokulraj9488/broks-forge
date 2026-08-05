package com.broksforge.fkge.traverse;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.kernel.api.LogPosition;

import java.util.List;

/**
 * A connecting walk between two nodes — the exhibited proof of traceability. Deterministic: among equal
 * shortest paths, the one whose edges are least in {@code (LogPosition, RevisionHash)} order is returned.
 */
public record Path(List<GraphNode> nodes, List<GraphEdge> edges, LogPosition asOf) {

    public Path {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** Number of edges (hops) in the walk. */
    public int length() {
        return edges.size();
    }
}
