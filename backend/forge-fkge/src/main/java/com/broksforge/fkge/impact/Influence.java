package com.broksforge.fkge.impact;

import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

/**
 * A closed-form structural rank of how load-bearing a node is: the size of its blast radius, tie-broken by
 * the length of its downstream critical path. No probabilistic centrality, no damping, no iteration.
 */
public record Influence(NodeId subject, int dependents, int criticalPathLength, LogPosition asOf) {

    /** The primary rank key: more dependents ⇒ more load-bearing. */
    public int score() {
        return dependents;
    }
}
