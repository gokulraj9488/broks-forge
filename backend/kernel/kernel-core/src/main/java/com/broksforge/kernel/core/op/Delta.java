package com.broksforge.kernel.core.op;

import java.util.List;

/**
 * The structural difference between two revisions (or any two canonical values).
 *
 * <p>A delta is a list of {@link Change}s, each located by a JSON-pointer-like path into the
 * canonical content. An empty change list means the two are identical — which, for content-addressed
 * revisions, means equal hashes. This is the generic basis of every comparison view, from a prompt
 * text diff to an architecture diff over closures (ADR-V2-0007, op 4).
 *
 * @param changes the differences, in stable (path-sorted) order; empty if identical
 */
public record Delta(List<Change> changes) {

    /** How a value at a path differs between the two sides. */
    public enum Kind { ADDED, REMOVED, CHANGED }

    /**
     * One located difference.
     *
     * @param path  the location in the canonical content (e.g. {@code /payload/text})
     * @param kind  whether the value was added, removed, or changed
     * @param left  the left value's canonical string, or null if absent
     * @param right the right value's canonical string, or null if absent
     */
    public record Change(String path, Kind kind, String left, String right) {
    }

    public Delta {
        changes = List.copyOf(changes);
    }

    /** @return true if the two sides are structurally identical */
    public boolean identical() {
        return changes.isEmpty();
    }
}
