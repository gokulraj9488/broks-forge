package com.broksforge.fkge.project;

import com.broksforge.fkge.depend.DependencyEngine;
import com.broksforge.fkge.impact.ImpactEngine;
import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.provenance.ProvenanceEngine;
import com.broksforge.fkge.reason.ConfidenceResult;
import com.broksforge.fkge.reason.ReasoningEngine;
import com.broksforge.kernel.api.NodeId;

/** Folds the other engines' outputs into a single deterministic {@link Summary}. */
public final class ProjectionEngine {

    private final GraphIndex index;
    private final ProvenanceEngine provenance;
    private final DependencyEngine dependency;
    private final ImpactEngine impact;
    private final ReasoningEngine reasoning;

    public ProjectionEngine(GraphIndex index, ProvenanceEngine provenance, DependencyEngine dependency,
                            ImpactEngine impact, ReasoningEngine reasoning) {
        this.index = index;
        this.provenance = provenance;
        this.dependency = dependency;
        this.impact = impact;
        this.reasoning = reasoning;
    }

    public Summary summarize(NodeId n) {
        GraphNode g = index.node(n).orElseThrow(() -> new IllegalArgumentException("unknown node: " + n));
        int deps = dependency.of(n).size();
        int radius = impact.of(n).radius();
        int prov = provenance.of(n).ancestors().size();
        ConfidenceResult c = reasoning.confidenceOf(n);
        return new Summary(n, g.kind(), g.label(), deps, radius, prov, c.confidence(), index.position());
    }
}
