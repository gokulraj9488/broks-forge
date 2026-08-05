package com.broksforge.fxp.review;

import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.reason.ConfidenceResult;
import com.broksforge.kernel.api.NodeId;

import java.util.List;

/** A review of a claim: its evidence and its conservative confidence, both from FKGE. */
public record ClaimReview(NodeId claim, List<GraphNode> evidence, ConfidenceResult confidence) {

    public ClaimReview {
        evidence = List.copyOf(evidence);
    }

    public boolean grounded() {
        return !evidence.isEmpty();
    }
}
