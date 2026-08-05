package com.broksforge.fvcs.history;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.RevisionHash;

import java.util.List;

/**
 * A projected commit in the version graph: its content hash, parent commit hashes (the DAG), the
 * snapshot it records, message, and the kernel-provided author and log position. Purely derived from the
 * append log — the history is a projection, never separate mutable storage.
 *
 * @param hash     the commit's content identity
 * @param parents  parent commit hashes (empty root; ≥2 merge)
 * @param snapshot the recorded snapshot (tree) hash
 * @param message  the commit message
 * @param author   the actor who made the commit (kernel provenance, Law 2)
 * @param position the log position at which it was recorded (the causal clock, Law 8)
 */
public record CommitNode(RevisionHash hash, List<RevisionHash> parents, RevisionHash snapshot,
                         String message, ActorId author, LogPosition position) {

    public CommitNode {
        parents = List.copyOf(parents);
    }

    /** @return true if this is a merge commit (≥2 parents) */
    public boolean isMerge() {
        return parents.size() >= 2;
    }
}
