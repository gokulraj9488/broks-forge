package com.broksforge.fkge;

import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.search.SearchEngine;
import com.broksforge.fvcs.diff.ChangeKind;
import com.broksforge.fvcs.diff.ChangeSet;
import com.broksforge.fvcs.repo.Branch;
import com.broksforge.fvcs.repo.CommitRef;
import com.broksforge.fvcs.repo.Repository;
import com.broksforge.fvcs.repo.SnapshotRef;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural similarity, version comparison (delegated to FVCS), version-node reachability, time travel. */
class SearchVersionTest {

    @Test
    @DisplayName("structural similarity: two providers with isomorphic neighborhoods match")
    void structuralSimilarity() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject p1 = kg.define(ObjectTypes.PROVIDER, TestSupport.obj("name", "anthropic"));
        kg.define(ObjectTypes.MODEL, TestSupport.obj("model_id", "sonnet-5"), Link.of(Verbs.USES, p1));
        KnowledgeObject p2 = kg.define(ObjectTypes.PROVIDER, TestSupport.obj("name", "openai"));
        kg.define(ObjectTypes.MODEL, TestSupport.obj("model_id", "gpt-9"), Link.of(Verbs.USES, p2));

        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(repo);
        assertTrue(fkge.similarTo(p1.node()).stream().anyMatch(g -> g.id().equals(p2.node())));
    }

    @Test
    @DisplayName("neighborhood signatures are content-based, so equal across independent kernels")
    void signatureDeterministicAcrossRuns() {
        TestSupport.Scenario s1 = TestSupport.scenario();
        TestSupport.Scenario s2 = TestSupport.scenario();
        String sig1 = new SearchEngine(GraphIndex.of(s1.repo)).signature(s1.agent);
        String sig2 = new SearchEngine(GraphIndex.of(s2.repo)).signature(s2.agent);
        assertEquals(sig1, sig2, "the same structure hashes identically regardless of node ids");
    }

    @Test
    @DisplayName("version comparison is delegated to FVCS diff; version nodes join the blast radius")
    void versionComparisonAndReachability() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject provider = kg.define(ObjectTypes.PROVIDER, TestSupport.obj("name", "anthropic"));
        KnowledgeObject model = kg.define(ObjectTypes.MODEL, TestSupport.obj("model_id", "sonnet-5"),
                Link.of(Verbs.USES, provider));
        KnowledgeObject prompt = kg.define(ObjectTypes.PROMPT, TestSupport.obj("text", "v1"));

        Branch main = repo.branch("main");
        SnapshotRef s1 = repo.snapshot("s1", List.of(provider, model, prompt));
        CommitRef c1 = repo.commit(main, s1, "c1");
        KnowledgeObject prompt2 = kg.addRevision(prompt, TestSupport.obj("text", "v2"));
        CommitRef c2 = repo.commit(main, repo.snapshot("s2", List.of(provider, model, prompt2)), "c2");

        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(repo);
        ChangeSet cs = fkge.whatChanged(c1, c2);
        assertFalse(cs.identical());
        assertEquals(1, cs.of(ChangeKind.CHANGED).size());

        // The snapshot (ArtifactPackage) that includes the model is downstream of it (composition).
        assertTrue(fkge.impactOf(model.node()).dependents().stream().anyMatch(g -> g.id().equals(s1.pkg().node())));
    }

    @Test
    @DisplayName("version comparison requires a repository-backed engine")
    void versionComparisonNeedsRepository() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject prompt = kg.define(ObjectTypes.PROMPT, TestSupport.obj("text", "v1"));
        Branch main = repo.branch("main");
        CommitRef c1 = repo.commit(main, repo.snapshot("s1", List.of(prompt)), "c1");

        KnowledgeGraphEngine graphOnly = KnowledgeGraphEngine.open(repo.kernel(), repo.org(), repo.ontology());
        assertThrows(IllegalStateException.class, () -> graphOnly.whatChanged(c1, c1));
    }

    @Test
    @DisplayName("time travel: as-of an earlier position, later facts are absent — deterministically")
    void timeTravel() {
        TestSupport.Scenario s = TestSupport.scenario();
        LogPosition agentPos = TestSupport.positionOf(s.repo, s.agent);
        KnowledgeGraphEngine past = KnowledgeGraphEngine.open(s.repo).asOf(agentPos);
        assertTrue(past.index().node(s.agent).isPresent(), "the agent existed at this position");
        assertTrue(past.index().node(s.deployment).isEmpty(), "the deployment was appended later");
    }
}
