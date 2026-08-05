package com.broksforge.fkge.reason;

import com.broksforge.fkge.index.GraphNode;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.math.BigDecimal;
import java.util.List;

/**
 * The conservative confidence of a node: the {@code min} over its supporting claims (a conjunction is as
 * strong as its weakest link). {@code confidence} is null when the subject is not a truth-bearer (an
 * Artifact with no supporting claims). Never invents or raises a value — arithmetic on recorded numbers.
 */
public record ConfidenceResult(NodeId subject,
                               BigDecimal confidence,
                               NodeId weakestLink,
                               List<GraphNode> support,
                               LogPosition asOf) {

    public ConfidenceResult {
        support = List.copyOf(support);
    }

    public boolean defined() {
        return confidence != null;
    }
}
