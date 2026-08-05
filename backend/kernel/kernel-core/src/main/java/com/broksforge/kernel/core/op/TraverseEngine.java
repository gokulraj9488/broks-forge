package com.broksforge.kernel.core.op;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.core.store.GraphIndex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Breadth-first traversal of the graph (ADR-V2-0007, op 3). Follows the {@link GraphIndex}'s live
 * edges — intrinsic (from revision content) and non-retracted extrinsic — in the requested
 * {@link Query.Direction}, restricted to the requested families, to the requested depth.
 */
public final class TraverseEngine {

    private final GraphIndex index;

    public TraverseEngine(GraphIndex index) {
        this.index = index;
    }

    /**
     * @param org   the organization
     * @param query the traversal specification
     * @return the reachable subgraph (nodes and the edges walked)
     */
    public Subgraph traverse(OrgId org, Query query) {
        Set<Address> nodes = new LinkedHashSet<>();
        // A LinkedHashSet keeps discovery order while collapsing the duplicate an edge would
        // otherwise get in BOTH mode (seen once as an out-edge and once as an in-edge).
        Set<Edge> edges = new LinkedHashSet<>();
        Set<Address> visited = new LinkedHashSet<>();

        Deque<Step> frontier = new ArrayDeque<>();
        nodes.add(query.start());
        frontier.add(new Step(query.start(), 0));

        while (!frontier.isEmpty()) {
            Step step = frontier.removeFirst();
            if (!visited.add(step.address())) {
                continue;
            }
            if (step.depth() >= query.maxDepth()) {
                continue;
            }
            for (Edge edge : neighbourEdges(org, step.address(), query)) {
                if (!query.follows(edge.family())) {
                    continue;
                }
                edges.add(edge);
                Address next = nextAddress(edge, step.address());
                if (nodes.add(next)) {
                    // newly seen node
                }
                if (!visited.contains(next)) {
                    frontier.add(new Step(next, step.depth() + 1));
                }
            }
        }
        return new Subgraph(nodes, new ArrayList<>(edges));
    }

    private List<Edge> neighbourEdges(OrgId org, Address at, Query query) {
        List<Edge> result = new ArrayList<>();
        if (query.direction() == Query.Direction.OUT || query.direction() == Query.Direction.BOTH) {
            result.addAll(index.outEdges(org, at));
        }
        if (query.direction() == Query.Direction.IN || query.direction() == Query.Direction.BOTH) {
            result.addAll(index.inEdges(org, at));
        }
        return result;
    }

    private static Address nextAddress(Edge edge, Address from) {
        // For an OUT edge we arrived via 'from' == edge.from and move to edge.to; for an IN edge we
        // arrived via 'from' == edge.to and move to edge.from. Compare by URI to be address-shape safe.
        return edge.from().toUri().equals(from.toUri()) ? edge.to() : edge.from();
    }

    private record Step(Address address, int depth) {
    }
}
