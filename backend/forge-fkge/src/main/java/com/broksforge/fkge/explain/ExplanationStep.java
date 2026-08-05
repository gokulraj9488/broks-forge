package com.broksforge.fkge.explain;

import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.Verb;

/** One "because" step of a proof tree: {@code from --verb(family)--> to} at a given depth. */
public record ExplanationStep(NodeId from, Verb verb, EdgeFamily family, NodeId to, int depth) {}
