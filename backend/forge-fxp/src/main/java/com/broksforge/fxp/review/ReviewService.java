package com.broksforge.fxp.review;

import com.broksforge.fkge.KnowledgeGraphEngine;
import com.broksforge.fvcs.diff.ChangeSet;
import com.broksforge.fvcs.diff.ObjectChange;
import com.broksforge.fvcs.repo.CommitRef;
import com.broksforge.fvcs.repo.Repository;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;
import com.broksforge.kernel.api.NodeId;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge Review — the judgement experience. It reads change and evidence through the platform and records
 * verdicts as first-class decisions, reusing the frozen AI-PR triad ({@code approves}/{@code rejects}).
 * A review that approves a deployment is itself a lawful, attributed, explainable kernel fact.
 */
public final class ReviewService {

    private final Repository repo;
    private final KnowledgeGraph kg;
    private final java.util.function.Supplier<KnowledgeGraphEngine> engines;

    public ReviewService(Repository repo, java.util.function.Supplier<KnowledgeGraphEngine> engines) {
        this.repo = repo;
        this.kg = repo.knowledge();
        this.engines = engines;
    }

    /** Review the change between two commits: the semantic diff plus the blast radius of each change. */
    public CommitReview reviewCommit(CommitRef from, CommitRef to) {
        KnowledgeGraphEngine fkge = engines.get();
        ChangeSet changes = repo.diff(from, to);
        List<ChangeImpact> impacts = new ArrayList<>();
        for (ObjectChange c : changes.changes()) {
            int radius = fkge.impactOf(c.node()).radius();
            impacts.add(new ChangeImpact(c.node(), c.kind(), radius));
        }
        return new CommitReview(from, to, changes, impacts, fkge.index().position());
    }

    /** The semantic diff between two commits — delegated to FVCS. */
    public ChangeSet semanticDiff(CommitRef from, CommitRef to) {
        return repo.diff(from, to);
    }

    public ClaimReview reviewClaim(NodeId claim) {
        KnowledgeGraphEngine fkge = engines.get();
        return new ClaimReview(claim, fkge.evidenceFor(claim), fkge.confidenceOf(claim));
    }

    public DecisionReview reviewDecision(NodeId decision) {
        KnowledgeGraphEngine fkge = engines.get();
        return new DecisionReview(decision, fkge.explain(decision), fkge.confidenceOf(decision));
    }

    /**
     * Approve a decision (e.g. a deployment) — records an {@code Approval} that {@code approves} it. The
     * approval is a reviewer's act of will (a judgment-call), attributed to the acting actor by the kernel.
     */
    public KnowledgeObject approve(KnowledgeObject decision, String statement) {
        KnowledgeObject approval = kg.define(ObjectTypes.APPROVAL,
                CanonicalValue.objectBuilder().put("statement", statement).put("judgment-call", true).build());
        kg.relate(approval, Verbs.APPROVES, decision);
        return approval;
    }

    /** Reject a decision — records an {@code Approval} that {@code rejects} it, with the reason. */
    public KnowledgeObject reject(KnowledgeObject decision, String reason) {
        KnowledgeObject rejection = kg.define(ObjectTypes.APPROVAL,
                CanonicalValue.objectBuilder().put("statement", reason).put("judgment-call", true).build());
        kg.relate(rejection, Verbs.REJECTS, decision);
        return rejection;
    }
}
