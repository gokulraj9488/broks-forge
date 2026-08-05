package com.broksforge.fxp.studio;

import com.broksforge.fkge.KnowledgeGraphEngine;
import com.broksforge.fkge.explain.Explanation;
import com.broksforge.fvcs.repo.Branch;
import com.broksforge.fvcs.repo.CommitRef;
import com.broksforge.fvcs.repo.SnapshotRef;
import com.broksforge.fvcs.repo.TagRef;
import com.broksforge.fvcs.repo.TagRole;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.graph.KnowledgeView;
import com.broksforge.knowledge.ontology.ObjectType;
import com.broksforge.kernel.api.Verb;

import java.math.BigDecimal;
import java.util.List;

/**
 * Forge Studio — the authoring experience. The only write path in FXP: every act becomes a lawful kernel
 * fact (attributed, timestamped, hashed). Studio holds no engineering logic; it composes the platform's
 * write APIs ({@link KnowledgeGraph}, {@code Repository}) and the read/explain API ({@link KnowledgeGraphEngine}).
 */
public final class StudioService {

    private final com.broksforge.fvcs.repo.Repository repo;
    private final KnowledgeGraph kg;
    private final java.util.function.Supplier<KnowledgeGraphEngine> engines;

    public StudioService(com.broksforge.fvcs.repo.Repository repo,
                         java.util.function.Supplier<KnowledgeGraphEngine> engines) {
        this.repo = repo;
        this.kg = repo.knowledge();
        this.engines = engines;
    }

    // ---- Create / revise artifacts and observations ----

    /** Create an engineering artifact (or single-revision observation) with its intrinsic links. */
    public KnowledgeObject create(ObjectType type, CanonicalValue payload, Link... links) {
        return kg.define(type, payload, links);
    }

    /** Record an observation of reality (single-revision by kind). */
    public KnowledgeObject recordObservation(ObjectType type, CanonicalValue payload, Link... links) {
        return kg.define(type, payload, links);
    }

    /** Revise a revisable artifact — a new content-addressed revision of the same continuant. */
    public KnowledgeObject revise(KnowledgeObject object, CanonicalValue payload, Link... links) {
        return kg.addRevision(object, payload, links);
    }

    // ---- Author claims and decisions (laws enforced by the kernel) ----

    /**
     * Author a claim. The kernel's Law 5 requires statement + method + confidence and at least one piece
     * of evidence — passed as evidence links (e.g. {@code cites}). Studio never fabricates a belief; it
     * records one with its grounding.
     */
    public KnowledgeObject authorClaim(ObjectType claimType, String statement, String method,
                                       BigDecimal confidence, Link... evidence) {
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("statement", statement)
                .put("method", method)
                .put("confidence", CanonicalValue.of(confidence))
                .build();
        return kg.define(claimType, payload, evidence);
    }

    /**
     * Record a decision. The kernel's Law 6 requires it to rest on claims or be marked a judgment-call.
     * Pass the cited claims as {@code rests_on} links, or set {@code judgmentCall} true.
     */
    public KnowledgeObject recordDecision(ObjectType decisionType, String statement,
                                          boolean judgmentCall, Link... links) {
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("statement", statement)
                .put("judgment-call", judgmentCall)
                .build();
        return kg.define(decisionType, payload, links);
    }

    /** Assert an extrinsic relationship (e.g. a causality edge {@code caused}). */
    public void link(KnowledgeObject from, Verb verb, KnowledgeObject to) {
        kg.relate(from, verb, to);
    }

    // ---- Versions ----

    public Branch branch(String line) {
        return repo.branch(line);
    }

    public SnapshotRef snapshot(String name, List<KnowledgeObject> members) {
        return repo.snapshot(name, members);
    }

    public CommitRef commit(Branch branch, SnapshotRef snapshot, String message) {
        return repo.commit(branch, snapshot, message);
    }

    public TagRef tag(String name, CommitRef commit, TagRole role, String message) {
        return repo.tag(name, commit, role, message);
    }

    // ---- Browse & navigate ----

    public List<KnowledgeObject> browse() {
        return kg.view().allObjects();
    }

    public List<KnowledgeObject> browse(ObjectType type) {
        return kg.view().objects(type);
    }

    public List<com.broksforge.fvcs.history.CommitNode> history(Branch branch) {
        return repo.history(branch);
    }

    public SnapshotRef checkout(CommitRef commit) {
        return repo.checkout(commit);
    }

    public KnowledgeView view() {
        return kg.view();
    }

    // ---- Explain (read-through to FKGE) ----

    public Explanation explain(com.broksforge.kernel.api.NodeId node) {
        return engines.get().explain(node);
    }
}
