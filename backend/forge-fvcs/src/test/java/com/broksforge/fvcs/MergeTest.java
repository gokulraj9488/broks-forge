package com.broksforge.fvcs;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.fvcs.compat.CompatibilityResult;
import com.broksforge.fvcs.diff.ChangeKind;
import com.broksforge.fvcs.diff.ChangeSet;
import com.broksforge.fvcs.merge.Conflict;
import com.broksforge.fvcs.merge.ConflictKind;
import com.broksforge.fvcs.merge.MergeResult;
import com.broksforge.fvcs.repo.Branch;
import com.broksforge.fvcs.repo.CommitRef;
import com.broksforge.fvcs.repo.Repository;
import com.broksforge.fvcs.repo.SnapshotRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Diff, three-way merge (clean + structural conflict + resolution), and compatibility. */
class MergeTest {

    @Test
    @DisplayName("diff reports a CHANGED continuant across two commits")
    void diffChanged() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject prompt = TestSupport.prompt(kg, "v1");
        Branch main = repo.branch("main");
        CommitRef c1 = repo.commit(main, repo.snapshot("s1", List.of(prompt)), "c1");
        KnowledgeObject prompt2 = TestSupport.revisePrompt(kg, prompt, "v2");
        CommitRef c2 = repo.commit(main, repo.snapshot("s2", List.of(prompt2)), "c2");

        ChangeSet cs = repo.diff(c1, c2);
        assertFalse(cs.identical());
        assertEquals(1, cs.of(ChangeKind.CHANGED).size());
        assertEquals(prompt.node(), cs.of(ChangeKind.CHANGED).get(0).node());
    }

    @Test
    @DisplayName("clean three-way merge: disjoint edits combine; the merge commit has two parents")
    void cleanMerge() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject provider = TestSupport.provider(kg, "anthropic");
        KnowledgeObject model = TestSupport.model(kg, provider, "sonnet-5");
        KnowledgeObject prompt = TestSupport.prompt(kg, "v1");

        Branch main = repo.branch("main");
        CommitRef base = repo.commit(main, repo.snapshot("base", List.of(provider, model, prompt)), "base");
        Branch exp = repo.branchFrom("exp", base);

        KnowledgeObject promptM = TestSupport.revisePrompt(kg, prompt, "main-edit");
        repo.commit(main, repo.snapshot("m", List.of(provider, model, promptM)), "main prompt");
        KnowledgeObject modelE = TestSupport.reviseModel(kg, model, provider, "sonnet-6");
        repo.commit(exp, repo.snapshot("e", List.of(provider, modelE, prompt)), "exp model");

        MergeResult mr = repo.merge(main, exp, "merge exp");
        assertTrue(mr.clean(), () -> "expected clean merge, got " + mr.conflicts());
        assertTrue(mr.mergeCommit().orElseThrow().isMerge());
        assertEquals(2, mr.mergeCommit().orElseThrow().parents().size());

        // The merged snapshot pins both divergent edits.
        SnapshotRef merged = repo.checkout(repo.head(main).orElseThrow());
        assertTrue(merged.members().contains(promptM.hash()));
        assertTrue(merged.members().contains(modelE.hash()));
    }

    @Test
    @DisplayName("merge is deterministic: the same logical merge yields the same snapshot hash across runs")
    void mergeDeterministic() {
        RevisionHash first = runCleanMergeAndReturnMergedSnapshotHash();
        RevisionHash second = runCleanMergeAndReturnMergedSnapshotHash();
        assertEquals(first, second, "merged snapshot hash must be reproducible (content-addressed)");
    }

    private static RevisionHash runCleanMergeAndReturnMergedSnapshotHash() {
        Repository repo = TestSupport.repo();     // fresh kernel (random node minting)
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject provider = TestSupport.provider(kg, "anthropic");
        KnowledgeObject model = TestSupport.model(kg, provider, "sonnet-5");
        KnowledgeObject prompt = TestSupport.prompt(kg, "v1");
        Branch main = repo.branch("main");
        CommitRef base = repo.commit(main, repo.snapshot("base", List.of(provider, model, prompt)), "base");
        Branch exp = repo.branchFrom("exp", base);
        KnowledgeObject promptM = TestSupport.revisePrompt(kg, prompt, "main-edit");
        repo.commit(main, repo.snapshot("m", List.of(provider, model, promptM)), "main prompt");
        KnowledgeObject modelE = TestSupport.reviseModel(kg, model, provider, "sonnet-6");
        repo.commit(exp, repo.snapshot("e", List.of(provider, modelE, prompt)), "exp model");
        MergeResult mr = repo.merge(main, exp, "merge exp");
        return repo.checkout(mr.mergeCommit().orElseThrow()).hash();
    }

    @Test
    @DisplayName("structural conflict: concurrent edits to the same object block the merge (modify/modify)")
    void structuralConflict() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject prompt = TestSupport.prompt(kg, "v1");

        Branch main = repo.branch("main");
        CommitRef base = repo.commit(main, repo.snapshot("base", List.of(prompt)), "base");
        Branch exp = repo.branchFrom("exp", base);

        KnowledgeObject promptA = TestSupport.revisePrompt(kg, prompt, "A");
        repo.commit(main, repo.snapshot("a", List.of(promptA)), "main A");
        KnowledgeObject promptB = TestSupport.revisePrompt(kg, prompt, "B");
        repo.commit(exp, repo.snapshot("b", List.of(promptB)), "exp B");

        MergeResult mr = repo.merge(main, exp, "merge");
        assertFalse(mr.clean());
        assertEquals(1, mr.conflicts().size());
        Conflict c = mr.conflicts().get(0);
        assertEquals(ConflictKind.MODIFY_MODIFY, c.kind());
        assertEquals(prompt.node(), c.node());
        // The target branch was NOT advanced by a blocked merge.
        assertFalse(repo.head(main).orElseThrow().isMerge());
    }

    @Test
    @DisplayName("a supplied resolution turns a structural conflict into a clean merge")
    void resolvedMerge() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject prompt = TestSupport.prompt(kg, "v1");

        Branch main = repo.branch("main");
        CommitRef base = repo.commit(main, repo.snapshot("base", List.of(prompt)), "base");
        Branch exp = repo.branchFrom("exp", base);

        KnowledgeObject promptA = TestSupport.revisePrompt(kg, prompt, "A");
        repo.commit(main, repo.snapshot("a", List.of(promptA)), "main A");
        KnowledgeObject promptB = TestSupport.revisePrompt(kg, prompt, "B");
        repo.commit(exp, repo.snapshot("b", List.of(promptB)), "exp B");

        Map<NodeId, RevisionHash> resolutions = Map.of(prompt.node(), promptA.hash());
        MergeResult mr = repo.merge(main, exp, "merge (take A)", resolutions);
        assertTrue(mr.clean());
        SnapshotRef merged = repo.checkout(repo.head(main).orElseThrow());
        assertTrue(merged.members().contains(promptA.hash()));
        assertFalse(merged.members().contains(promptB.hash()));
    }

    @Test
    @DisplayName("compatibility: removing a required object makes a snapshot an incompatible replacement")
    void compatibility() {
        Repository repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        KnowledgeObject provider = TestSupport.provider(kg, "anthropic");
        KnowledgeObject model = TestSupport.model(kg, provider, "sonnet-5");
        KnowledgeObject prompt = TestSupport.prompt(kg, "v1");

        SnapshotRef full = repo.snapshot("full", List.of(provider, model, prompt));
        SnapshotRef reduced = repo.snapshot("reduced", List.of(provider, prompt)); // model removed

        CompatibilityResult result = repo.checkCompatibility(full, reduced);
        assertFalse(result.compatible());
        assertFalse(result.issues().isEmpty());
    }
}
