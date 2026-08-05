package com.broksforge.fkge.project;

import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.math.BigDecimal;

/**
 * A deterministic one-node summary: what it is, how much rests on it and it rests on, and its conservative
 * confidence. A projection, not a narrative — every field is a counted or recorded value.
 */
public record Summary(NodeId subject,
                      Kind kind,
                      String label,
                      int dependencyCount,
                      int blastRadius,
                      int provenanceSize,
                      BigDecimal confidence,
                      LogPosition asOf) {
}
