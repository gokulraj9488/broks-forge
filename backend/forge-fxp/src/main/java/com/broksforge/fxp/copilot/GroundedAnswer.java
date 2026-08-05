package com.broksforge.fxp.copilot;

import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

/**
 * A Copilot answer that always carries its {@link Proof}. If {@code grounded} is false the platform had no
 * facts and the Copilot refused — the narrative is a refusal, and no language model was consulted. The proof
 * lets any consumer verify the narrative against the platform, at the cited {@code asOf} position.
 */
public record GroundedAnswer(NodeId subject, Intent intent, boolean grounded,
                             String narrative, Proof proof, LogPosition asOf) {

    static GroundedAnswer refusal(NodeId subject, Intent intent, String why, LogPosition asOf) {
        return new GroundedAnswer(subject, intent, false, why,
                new Proof(intent, subject, java.util.List.of(), java.util.List.of(), asOf), asOf);
    }
}
