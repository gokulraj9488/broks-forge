package com.broksforge.fkge.index;

import java.util.Comparator;

/**
 * The single determinism discipline of FKGE: every set returned and every tie broken uses the kernel's
 * total order {@code (LogPosition, RevisionHash)}. No algorithm may depend on hash-map iteration order,
 * wall-clock, or randomness — so the same query over the same log prefix yields byte-identical results.
 */
public final class Order {

    private Order() {}

    /** Total order over nodes: ascending log position, then revision hash. */
    public static final Comparator<GraphNode> NODES =
            Comparator.comparingLong((GraphNode n) -> n.position().value())
                    .thenComparing(n -> n.hash().toString());

    /** Total order over edges: ascending position, then from, verb, to. */
    public static final Comparator<GraphEdge> EDGES =
            Comparator.comparingLong((GraphEdge e) -> e.position().value())
                    .thenComparing(e -> e.from().toString())
                    .thenComparing(e -> e.verb().name())
                    .thenComparing(e -> e.to().toString());
}
