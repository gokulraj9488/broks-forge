package com.broksforge.fvcs.merge;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.RevisionHash;

/**
 * One detected conflict. For structural conflicts, {@code node} identifies the contested continuant and
 * {@code base}/{@code ours}/{@code theirs} its three-way revisions (any may be null). {@code CRISS_CROSS}
 * carries no node.
 *
 * @param level  the conflict level
 * @param kind   the conflict shape
 * @param node   the contested continuant (null for CRISS_CROSS)
 * @param base   the merge-base revision (may be null)
 * @param ours   the target-side revision (may be null)
 * @param theirs the source-side revision (may be null)
 * @param detail a human-readable description
 */
public record Conflict(ConflictLevel level, ConflictKind kind, NodeId node,
                       RevisionHash base, RevisionHash ours, RevisionHash theirs, String detail) {

    /** @return a structural conflict */
    public static Conflict structural(ConflictKind kind, NodeId node,
                                      RevisionHash base, RevisionHash ours, RevisionHash theirs, String detail) {
        return new Conflict(ConflictLevel.STRUCTURAL, kind, node, base, ours, theirs, detail);
    }

    /** @return a criss-cross (multiple merge base) conflict */
    public static Conflict crissCross(String detail) {
        return new Conflict(ConflictLevel.STRUCTURAL, ConflictKind.CRISS_CROSS, null, null, null, null, detail);
    }
}
