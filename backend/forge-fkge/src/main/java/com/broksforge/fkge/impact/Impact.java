package com.broksforge.fkge.impact;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.util.List;
import java.util.Map;

/**
 * The blast radius of a node — everything downstream that has it in its provenance — with the longest
 * downstream chain and a per-kind tally. By the duality law this is provenance read in the other direction.
 */
public record Impact(NodeId subject,
                     List<GraphNode> dependents,
                     List<GraphEdge> edges,
                     List<GraphNode> criticalPath,
                     Map<Kind, Long> byKind,
                     LogPosition asOf) {

    public Impact {
        dependents = List.copyOf(dependents);
        edges = List.copyOf(edges);
        criticalPath = List.copyOf(criticalPath);
        byKind = Map.copyOf(byKind);
    }

    public int radius() {
        return dependents.size();
    }
}
