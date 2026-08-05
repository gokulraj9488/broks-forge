package com.broksforge.fxp.explore;

import com.broksforge.fkge.explain.Explanation;
import com.broksforge.fkge.impact.Impact;
import com.broksforge.fkge.provenance.Provenance;
import com.broksforge.fkge.reason.ConfidenceResult;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

/**
 * A deterministic answer to "why is this in production?" — history/provenance, the decision proof tree,
 * the confidence bound, and the blast radius, every part a reproducible platform fact. The narrative (if
 * any) is added by the Copilot; this object is the proof.
 */
public record ProductionDossier(NodeId subject,
                                Provenance provenance,
                                Explanation decisionProof,
                                ConfidenceResult confidence,
                                Impact impact,
                                LogPosition asOf) {
}
