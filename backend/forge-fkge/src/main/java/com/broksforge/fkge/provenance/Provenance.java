package com.broksforge.fkge.provenance;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.RevisionHash;

import java.util.List;

/**
 * The complete, causally-ordered derivation history of a node — where it came from, through what, decided
 * by whom, on what evidence. {@code certificate} is the subject's content-addressed revision hash, which is
 * a Merkle commitment over its whole structural closure: recomputing it must match, or the provenance is
 * stale/tampered.
 */
public record Provenance(NodeId subject,
                         List<GraphNode> ancestors,
                         List<GraphEdge> edges,
                         RevisionHash certificate,
                         LogPosition asOf) {

    public Provenance {
        ancestors = List.copyOf(ancestors);
        edges = List.copyOf(edges);
    }

    public boolean contains(NodeId id) {
        return ancestors.stream().anyMatch(n -> n.id().equals(id));
    }
}
