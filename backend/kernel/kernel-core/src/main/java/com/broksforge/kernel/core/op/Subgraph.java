package com.broksforge.kernel.core.op;

import com.broksforge.kernel.api.Address;

import java.util.List;
import java.util.Set;

/**
 * The result of a {@code traverse}: the set of addresses reached and the edges walked between them.
 *
 * @param nodes the addresses reached (including the start)
 * @param edges the edges traversed
 */
public record Subgraph(Set<Address> nodes, List<Edge> edges) {

    public Subgraph {
        nodes = Set.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** @return true if the subgraph contains only the start node and no edges */
    public boolean isSingleton() {
        return edges.isEmpty() && nodes.size() == 1;
    }
}
