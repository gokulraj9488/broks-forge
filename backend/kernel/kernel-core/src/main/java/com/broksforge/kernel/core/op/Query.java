package com.broksforge.kernel.core.op;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;

import java.util.EnumSet;
import java.util.Set;

/**
 * A traversal specification — the closed input to {@code traverse}.
 *
 * <p>A query starts at an address and walks edges in a {@link Direction}, restricted to a set of
 * {@link EdgeFamily} (empty means all five), to a maximum depth. This is the reusable pattern that
 * also serves as a subscription predicate (ADR-V2-0007, ADR-V2-0008).
 *
 * @param start     the starting address
 * @param families  the families to follow; empty means all
 * @param direction the direction to walk
 * @param maxDepth  the maximum number of hops (0 returns just the start node)
 */
public record Query(Address start, Set<EdgeFamily> families, Direction direction, int maxDepth) {

    /** The direction in which to follow edges from a node. */
    public enum Direction { OUT, IN, BOTH }

    public Query {
        if (start == null) {
            throw new IllegalArgumentException("query start must not be null");
        }
        if (direction == null) {
            throw new IllegalArgumentException("query direction must not be null");
        }
        if (maxDepth < 0) {
            throw new IllegalArgumentException("query maxDepth must not be negative");
        }
        families = families == null ? EnumSet.noneOf(EdgeFamily.class) : Set.copyOf(families);
    }

    /**
     * @param start the starting address
     * @return a query that follows all families outward to depth 3
     */
    public static Query neighbors(Address start) {
        return new Query(start, EnumSet.noneOf(EdgeFamily.class), Direction.OUT, 3);
    }

    /**
     * @param family a family
     * @return true if this query follows the given family (empty family set means all)
     */
    public boolean follows(EdgeFamily family) {
        return families.isEmpty() || families.contains(family);
    }
}
