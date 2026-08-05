package com.broksforge.fvcs.merge;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.RevisionHash;

import java.util.List;
import java.util.Map;

/**
 * The pure result of the three-way merge computation: the structural conflicts (if any) and, when there
 * are none, the merged member set (continuant → chosen revision) from which a merged snapshot is built.
 *
 * @param conflicts     structural conflicts that block the merge (empty if it auto-merges)
 * @param mergedMembers the reconciled members (only meaningful when {@link #clean()})
 */
public record MergePlan(List<Conflict> conflicts, Map<NodeId, RevisionHash> mergedMembers) {

    public MergePlan {
        conflicts = List.copyOf(conflicts);
        mergedMembers = Map.copyOf(mergedMembers);
    }

    /** @return true if there are no blocking structural conflicts */
    public boolean clean() {
        return conflicts.isEmpty();
    }
}
