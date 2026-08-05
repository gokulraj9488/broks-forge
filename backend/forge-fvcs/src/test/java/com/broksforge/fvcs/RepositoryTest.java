package com.broksforge.fvcs;

import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.Name;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.fvcs.history.CommitNode;
import com.broksforge.fvcs.repo.Branch;
import com.broksforge.fvcs.repo.CommitRef;
import com.broksforge.fvcs.repo.Repository;
import com.broksforge.fvcs.repo.SnapshotRef;
import com.broksforge.fvcs.repo.TagRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Commit, branch, checkout, history, tags, reproducibility, deterministic time travel. */
class RepositoryTest {

    @Test
    @DisplayName("commit advances a branch; history is a parent-linked DAG, newest first")
    void commitAndHistory() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject provider = TestSupport.provider(kg, "anthropic");
        KnowledgeObject model = TestSupport.model(kg, provider, "sonnet-5");
        KnowledgeObject prompt = TestSupport.prompt(kg, "v1");

        Branch main = repo.branch("main");
        CommitRef c1 = repo.commit(main, repo.snapshot("s1", List.of(provider, model, prompt)), "initial");
        KnowledgeObject prompt2 = TestSupport.revisePrompt(kg, prompt, "v2");
        CommitRef c2 = repo.commit(main, repo.snapshot("s2", List.of(provider, model, prompt2)), "prompt v2");

        assertEquals(c2.hash(), repo.head(main).orElseThrow().hash());
        assertTrue(c2.parents().contains(c1.hash()));
        assertTrue(c1.parents().isEmpty());

        List<CommitNode> hist = repo.history(main);
        assertEquals(2, hist.size());
        assertEquals(c2.hash(), hist.get(0).hash());     // newest first
        assertEquals(c1.hash(), hist.get(1).hash());
        assertTrue(hist.get(0).position().value() > hist.get(1).position().value());
        assertTrue(repo.kernel().verifyChain(repo.org()));
    }

    @Test
    @DisplayName("checkout returns the pinned snapshot; snapshots are content-addressed (reproducible)")
    void checkoutAndReproducibility() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject provider = TestSupport.provider(kg, "anthropic");
        KnowledgeObject prompt = TestSupport.prompt(kg, "v1");

        Branch main = repo.branch("main");
        SnapshotRef s1 = repo.snapshot("s1", List.of(provider, prompt));
        CommitRef c1 = repo.commit(main, s1, "initial");

        SnapshotRef checkedOut = repo.checkout(c1);
        assertEquals(s1.hash(), checkedOut.hash());
        // Identical content (same name + same pinned members) is the same version (content-addressed
        // dedup) — building it again yields the same hash, even as a distinct continuant.
        SnapshotRef s1again = repo.snapshot("s1", List.of(provider, prompt));
        assertEquals(s1.hash(), s1again.hash());
    }

    @Test
    @DisplayName("deterministic time travel: checkoutAt reconstructs a past branch head")
    void timeTravel() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject provider = TestSupport.provider(kg, "anthropic");
        KnowledgeObject prompt = TestSupport.prompt(kg, "v1");

        Branch main = repo.branch("main");
        SnapshotRef s1 = repo.snapshot("s1", List.of(provider, prompt));
        repo.commit(main, s1, "c1");
        long posAfterC1 = repo.kernel().log(repo.org()).size();      // positions are 1..n

        KnowledgeObject prompt2 = TestSupport.revisePrompt(kg, prompt, "v2");
        SnapshotRef s2 = repo.snapshot("s2", List.of(provider, prompt2));
        repo.commit(main, s2, "c2");

        assertEquals(s2.hash(), repo.checkout(repo.head(main).orElseThrow()).hash());  // now
        assertEquals(s1.hash(), repo.checkoutAt(main, new LogPosition(posAfterC1)).orElseThrow().hash()); // then
    }

    @Test
    @DisplayName("branchFrom forks a line; tags mark a commit with an immovable name")
    void branchAndTag() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject prompt = TestSupport.prompt(kg, "v1");

        Branch main = repo.branch("main");
        CommitRef c1 = repo.commit(main, repo.snapshot("s1", List.of(prompt)), "c1");

        Branch exp = repo.branchFrom("exp/x", c1);
        assertEquals(c1.hash(), repo.head(exp).orElseThrow().hash());

        repo.tag("release/1.0.0", c1, TagRole.RELEASE, "GA");
        assertEquals(c1.commit().address(), repo.kernel().resolve(repo.org(), Name.of("tag/release/1.0.0")).orElseThrow());
        assertFalse(repo.history(main).isEmpty());
    }
}
