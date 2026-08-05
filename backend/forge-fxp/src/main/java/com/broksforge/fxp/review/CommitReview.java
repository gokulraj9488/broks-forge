package com.broksforge.fxp.review;

import com.broksforge.fvcs.diff.ChangeSet;
import com.broksforge.fvcs.repo.CommitRef;
import com.broksforge.kernel.api.LogPosition;

import java.util.List;

/**
 * A review of the change between two commits: the semantic diff (from FVCS) and, for each changed
 * continuant, its blast radius (from FKGE) so a reviewer sees not just what changed but what it endangers.
 */
public record CommitReview(CommitRef from, CommitRef to, ChangeSet changes,
                           List<ChangeImpact> impacts, LogPosition asOf) {

    public CommitReview {
        impacts = List.copyOf(impacts);
    }

    public boolean clean() {
        return changes.identical();
    }
}
