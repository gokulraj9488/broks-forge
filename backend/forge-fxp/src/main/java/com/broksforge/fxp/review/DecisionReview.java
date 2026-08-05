package com.broksforge.fxp.review;

import com.broksforge.fkge.explain.Explanation;
import com.broksforge.fkge.reason.ConfidenceResult;
import com.broksforge.kernel.api.NodeId;

/** A review of a decision: its proof tree (why it was made) and the confidence of what it rests on. */
public record DecisionReview(NodeId decision, Explanation explanation, ConfidenceResult confidence) {

    public boolean justified() {
        return explanation.complete();
    }
}
