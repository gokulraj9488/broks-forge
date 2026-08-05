package com.broksforge.fxp.copilot;

import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.util.List;

/**
 * The machine-checkable proof behind a Copilot answer: the platform facts (from FKGE) and the node ids they
 * cite, at the {@code asOf} position. The Copilot's narrative is a rendering of this; the proof is the truth.
 * An empty proof means the platform has nothing to say — and the Copilot must refuse rather than invent.
 */
public record Proof(Intent intent, NodeId subject, List<String> facts, List<NodeId> citations, LogPosition asOf) {

    public Proof {
        facts = List.copyOf(facts);
        citations = List.copyOf(citations);
    }

    public boolean empty() {
        return facts.isEmpty();
    }
}
