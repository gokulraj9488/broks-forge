package com.broksforge.fxp.explore;

import com.broksforge.fkge.KnowledgeGraphEngine;
import com.broksforge.fkge.depend.DependencySet;
import com.broksforge.fkge.explain.Explanation;
import com.broksforge.fkge.impact.Impact;
import com.broksforge.fkge.impact.Influence;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.provenance.Provenance;
import com.broksforge.fkge.reason.CausalTrace;
import com.broksforge.fkge.reason.ConfidenceResult;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.util.List;
import java.util.function.Supplier;

/**
 * Forge Explorer — the understanding experience. A thin, read-only projection of FKGE: it writes nothing
 * and computes nothing of its own. Each call obtains a fresh engine reflecting the current log (a pure
 * projection), and each returned FKGE proof carries the {@code asOf} position it was computed at, so every
 * exploration is reproducible.
 */
public final class ExplorerService {

    private final Supplier<KnowledgeGraphEngine> engines;

    public ExplorerService(Supplier<KnowledgeGraphEngine> engines) {
        this.engines = engines;
    }

    public Provenance provenance(NodeId n) {
        return engines.get().provenanceOf(n);
    }

    public List<GraphNode> lineage(NodeId n) {
        return engines.get().lineageOf(n);
    }

    public DependencySet dependencies(NodeId n) {
        return engines.get().dependenciesOf(n);
    }

    public Impact impact(NodeId n) {
        return engines.get().impactOf(n);
    }

    public Influence influence(NodeId n) {
        return engines.get().influence(n);
    }

    public List<GraphNode> criticalPath(NodeId n) {
        return engines.get().criticalPath(n);
    }

    public CausalTrace rootCause(NodeId incident) {
        return engines.get().rootCause(incident);
    }

    public ConfidenceResult confidence(NodeId n) {
        return engines.get().confidenceOf(n);
    }

    public List<GraphNode> evidence(NodeId claim) {
        return engines.get().evidenceFor(claim);
    }

    public Explanation explain(NodeId n) {
        return engines.get().explain(n);
    }

    public List<GraphNode> similar(NodeId n) {
        return engines.get().similarTo(n);
    }

    /** Re-bind exploration to an earlier log prefix for deterministic time travel. */
    public ExplorerService asOf(LogPosition position) {
        return new ExplorerService(() -> engines.get().asOf(position));
    }

    /**
     * The "why is this in production?" dossier (reference workflow W3): a deterministic bundle of history,
     * provenance, evidence, decisions, and confidence — every field a platform fact, none narrated. Folded
     * once from a single engine snapshot so all parts share one {@code asOf}.
     */
    public ProductionDossier dossier(NodeId subject) {
        KnowledgeGraphEngine fkge = engines.get();
        Provenance prov = fkge.provenanceOf(subject);
        Explanation why = fkge.explain(subject);
        ConfidenceResult conf = fkge.confidenceOf(subject);
        Impact impact = fkge.impactOf(subject);
        return new ProductionDossier(subject, prov, why, conf, impact, prov.asOf());
    }
}
