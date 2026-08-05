package com.broksforge.fkge.reason;

import com.broksforge.fkge.index.GraphNode;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.util.List;

/**
 * The causes of an effect, ordered by log position (the causal clock). {@code anomalies} lists any putative
 * cause that occurs <em>after</em> its effect — a data defect surfaced, never traversed as valid causation.
 * {@code sound()} iff there are none.
 */
public record CausalTrace(NodeId effect, List<GraphNode> causes, List<String> anomalies, LogPosition asOf) {

    public CausalTrace {
        causes = List.copyOf(causes);
        anomalies = List.copyOf(anomalies);
    }

    public boolean sound() {
        return anomalies.isEmpty();
    }
}
