package com.broksforge.fxp.review;

import com.broksforge.fvcs.diff.ChangeKind;
import com.broksforge.kernel.api.NodeId;

/** One changed continuant in a review, with how much rests on it (its blast radius). */
public record ChangeImpact(NodeId node, ChangeKind kind, int blastRadius) {}
