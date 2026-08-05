package com.broksforge.fkge.explain;

/**
 * How a leaf of an explanation proof-tree bottoms out. The first three are proper axioms; {@link #FRONTIER}
 * is a gap — a node that should be grounded but whose walk ended unresolved.
 */
public enum LeafKind {
    /** Reality recorded — a primary fact. */
    OBSERVATION,
    /** An artifact with no further derivation/composition — a primary input. */
    PRIMARY_ARTIFACT,
    /** A decision that cites no prior claim — "because a human decided." */
    JUDGMENT_CALL,
    /** An ungrounded node (e.g. a claim with no evidence): the explanation names it as a gap. */
    FRONTIER
}
