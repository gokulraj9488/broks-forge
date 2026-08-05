package com.broksforge.fvcs.merge;

import com.broksforge.fvcs.repo.CommitRef;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of a merge. When {@link #clean()}, a merge {@link CommitRef} was created (with ≥2 parents)
 * and any {@code semanticWarnings}/{@code operationalWarnings} are advisory (the data merged, but
 * promotion/deploy should address them first). When not clean, {@code conflicts} lists the blocking
 * structural conflicts and no commit was made.
 *
 * @param mergeCommit        the merge commit, if the merge succeeded
 * @param conflicts          blocking structural conflicts (empty on success)
 * @param semanticWarnings   advisory semantic findings (Conflict Model §2)
 * @param operationalWarnings advisory operational findings (Conflict Model §2)
 */
public record MergeResult(Optional<CommitRef> mergeCommit, List<Conflict> conflicts,
                          List<String> semanticWarnings, List<String> operationalWarnings) {

    public MergeResult {
        conflicts = List.copyOf(conflicts);
        semanticWarnings = List.copyOf(semanticWarnings);
        operationalWarnings = List.copyOf(operationalWarnings);
    }

    /** @return true if the merge produced a commit (no blocking structural conflicts) */
    public boolean clean() {
        return mergeCommit.isPresent();
    }
}
