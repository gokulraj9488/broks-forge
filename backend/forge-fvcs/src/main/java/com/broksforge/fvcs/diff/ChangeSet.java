package com.broksforge.fvcs.diff;

import java.util.List;

/**
 * The difference between two snapshots — the set of per-continuant changes. A change set is a pure
 * function of two content hashes, so it is deterministic and reproducible.
 *
 * @param changes the changes (excluding unchanged members)
 */
public record ChangeSet(List<ObjectChange> changes) {

    public ChangeSet {
        changes = List.copyOf(changes);
    }

    /** @return true if the two snapshots are identical (no changes) */
    public boolean identical() {
        return changes.isEmpty();
    }

    /** @param kind a change kind @return the changes of that kind */
    public List<ObjectChange> of(ChangeKind kind) {
        return changes.stream().filter(c -> c.kind() == kind).toList();
    }
}
