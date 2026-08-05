package com.broksforge.fvcs.repo;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.ontology.ObjectType;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Ontology;
import com.broksforge.knowledge.ontology.Verbs;
import com.broksforge.fvcs.compat.CompatibilityEngine;
import com.broksforge.fvcs.compat.CompatibilityResult;
import com.broksforge.fvcs.diff.ChangeSet;
import com.broksforge.fvcs.diff.DiffEngine;
import com.broksforge.fvcs.history.CommitNode;
import com.broksforge.fvcs.history.HistoryEngine;
import com.broksforge.fvcs.history.MergeBase;
import com.broksforge.fvcs.merge.Conflict;
import com.broksforge.fvcs.merge.MergeEngine;
import com.broksforge.fvcs.merge.MergePlan;
import com.broksforge.fvcs.merge.MergeResult;
import com.broksforge.fvcs.ontology.FvcsOntology;
import com.broksforge.fvcs.ontology.FvcsTypes;
import com.broksforge.fvcs.ontology.FvcsVerbs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The FVCS repository — the public version-control surface over one organization's knowledge graph.
 *
 * <p>It stores nothing of its own: branches/tags are kernel Names, snapshots are ArtifactPackages,
 * commits are Decisions, history is a projection. Every write is validated against the composed ontology
 * and recorded as a provenance-stamped, hash-chained fact. The only mutable state is the branch Names,
 * moved by compare-and-swap.
 */
public final class Repository {

    private final ForgeKernel kernel;
    private final OrgId org;
    private final ActorId actor;
    private final Ontology ontology;
    private final KnowledgeGraph kg;

    private Repository(ForgeKernel kernel, OrgId org, ActorId actor) {
        this.kernel = kernel;
        this.org = org;
        this.actor = actor;
        this.ontology = FvcsOntology.composed();
        this.kg = KnowledgeGraph.open(kernel, org, actor, ontology);
    }

    /**
     * @param kernel the kernel
     * @param org    the organization
     * @param actor  the signing actor
     * @return a repository bound to {@code org}/{@code actor}
     */
    public static Repository open(ForgeKernel kernel, OrgId org, ActorId actor) {
        if (kernel == null || org == null || actor == null) {
            throw new IllegalArgumentException("kernel, org, and actor must not be null");
        }
        return new Repository(kernel, org, actor);
    }

    /** @return the underlying knowledge graph */
    public KnowledgeGraph knowledge() {
        return kg;
    }

    /** @return the underlying kernel */
    public ForgeKernel kernel() {
        return kernel;
    }

    /** @return the composed (frozen ⊕ FVCS) ontology */
    public Ontology ontology() {
        return ontology;
    }

    /** @return the bound organization */
    public OrgId org() {
        return org;
    }

    // ---- snapshots & commits -----------------------------------------------------------------

    /**
     * Builds a snapshot (an ArtifactPackage) pinning the given artifact revisions.
     *
     * @param name    a label for the snapshot
     * @param members the artifact objects to pin (each an Artifact-kind knowledge object)
     * @return the snapshot reference
     */
    public SnapshotRef snapshot(String name, List<KnowledgeObject> members) {
        List<Link> links = new ArrayList<>(members.size());
        for (KnowledgeObject m : members) {
            links.add(Link.of(Verbs.INCLUDES, m));
        }
        KnowledgeObject pkg = kg.define(ObjectTypes.ARTIFACT_PACKAGE,
                CanonicalValue.objectBuilder().put("name", name).build(), links.toArray(new Link[0]));
        return new SnapshotRef(pkg);
    }

    /** @return a branch handle for {@code line} (created on first commit) */
    public Branch branch(String line) {
        return new Branch(line);
    }

    /**
     * Creates a new branch pointing at an existing commit (a fork).
     *
     * @param line the new branch line
     * @param from the commit to fork from
     * @return the new branch
     */
    public Branch branchFrom(String line, CommitRef from) {
        Branch b = new Branch(line);
        kernel.append(org, new AppendCommand.RepointName(b.name(), from.commit().address(), null), actor);
        return b;
    }

    /**
     * Commits a snapshot on a branch, advancing the branch head by compare-and-swap.
     *
     * @param branch   the branch
     * @param snapshot the snapshot to checkpoint
     * @param message  the commit message
     * @return the new commit
     */
    public CommitRef commit(Branch branch, SnapshotRef snapshot, String message) {
        Optional<Address.Revision> head = kernel.resolve(org, branch.name());
        List<Link> links = new ArrayList<>();
        links.add(Link.of(FvcsVerbs.RECORDS, snapshot.pkg()));
        head.ifPresent(h -> links.add(Link.of(FvcsVerbs.PARENT, commitObject(h))));

        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("message", message)
                .put("branch", branch.line())
                .put("snapshot", snapshot.hash().toString())
                .put("judgment-call", true)
                .build();
        KnowledgeObject commit = kg.define(FvcsTypes.COMMIT, payload, links.toArray(new Link[0]));
        kernel.append(org, new AppendCommand.RepointName(branch.name(),
                commit.address(), head.orElse(null)), actor);
        return new CommitRef(commit);
    }

    /** @param branch a branch @return its head commit, if any */
    public Optional<CommitRef> head(Branch branch) {
        return kernel.resolve(org, branch.name()).map(addr -> new CommitRef(commitObject(addr)));
    }

    // ---- checkout & history ------------------------------------------------------------------

    /**
     * @param commit a commit
     * @return the snapshot it records (checkout)
     */
    public SnapshotRef checkout(CommitRef commit) {
        return snapshotForHash(index(), commit.snapshotHash());
    }

    /**
     * Deterministic historical checkout: the snapshot a branch pointed at as of a past log position.
     *
     * @param branch the branch
     * @param asOf   the log position
     * @return the snapshot as of {@code asOf}, if the branch existed then
     */
    public Optional<SnapshotRef> checkoutAt(Branch branch, LogPosition asOf) {
        return kernel.resolveAt(org, branch.name(), asOf)
                .map(addr -> snapshotOfCommit(index(), addr.revision()));
    }

    /**
     * @param branch a branch
     * @return the commit history reachable from its head, newest first (empty if the branch is new)
     */
    public List<CommitNode> history(Branch branch) {
        Optional<CommitRef> head = head(branch);
        if (head.isEmpty()) {
            return List.of();
        }
        return HistoryEngine.of(kernel, org).history(head.get().hash());
    }

    // ---- diff & merge ------------------------------------------------------------------------

    /**
     * @param left  the left (old) commit
     * @param right the right (new) commit
     * @return the change set between their snapshots
     */
    public ChangeSet diff(CommitRef left, CommitRef right) {
        DiffEngine d = index();
        return d.diff(snapshotForHash(d, left.snapshotHash()), snapshotForHash(d, right.snapshotHash()));
    }

    /**
     * Merges {@code from} into {@code into} (three-way against the merge base). Structural conflicts block
     * the merge (no commit); otherwise a merge commit with two parents is created and the target branch
     * advanced, with any semantic/operational findings returned as advisory warnings.
     *
     * @param into    the target branch (advanced on success)
     * @param from    the source branch
     * @param message the merge message
     * @return the merge result
     */
    public MergeResult merge(Branch into, Branch from, String message) {
        return merge(into, from, message, Map.of());
    }

    /**
     * As {@link #merge(Branch, Branch, String)}, with caller-supplied conflict resolutions
     * (continuant → chosen revision).
     *
     * @param into        the target branch
     * @param from        the source branch
     * @param message     the merge message
     * @param resolutions resolutions for structural conflicts
     * @return the merge result
     */
    public MergeResult merge(Branch into, Branch from, String message, Map<NodeId, RevisionHash> resolutions) {
        CommitRef ours = head(into).orElseThrow(() -> new IllegalStateException("target branch has no head"));
        CommitRef theirs = head(from).orElseThrow(() -> new IllegalStateException("source branch has no head"));

        DiffEngine d = index();
        HistoryEngine h = HistoryEngine.of(kernel, org);
        MergeBase mb = h.mergeBase(ours.hash(), theirs.hash());
        if (mb.kind() == MergeBase.Kind.CRISS_CROSS) {
            return new MergeResult(Optional.empty(),
                    List.of(Conflict.crissCross("multiple merge bases; recursive merge not yet supported")),
                    List.of(), List.of());
        }

        SnapshotRef oursSnap = snapshotForHash(d, ours.snapshotHash());
        SnapshotRef theirsSnap = snapshotForHash(d, theirs.snapshotHash());
        Map<NodeId, RevisionHash> baseMembers = mb.kind() == MergeBase.Kind.SINGLE
                ? d.members(snapshotOfCommit(d, mb.single())) : Map.of();
        Map<NodeId, RevisionHash> oursMembers = d.members(oursSnap);
        Map<NodeId, RevisionHash> theirsMembers = d.members(theirsSnap);

        MergePlan plan = MergeEngine.plan(baseMembers, oursMembers, theirsMembers, resolutions);
        if (!plan.clean()) {
            return new MergeResult(Optional.empty(), plan.conflicts(), List.of(), List.of());
        }

        // Build the merged snapshot and the two-parent merge commit. Order members deterministically by
        // content hash: kernel reference order is significant, so an unordered map would make the merged
        // snapshot hash non-reproducible across runs (adversarial-review finding, determinism).
        List<Map.Entry<NodeId, RevisionHash>> entries = new ArrayList<>(plan.mergedMembers().entrySet());
        entries.sort(java.util.Comparator.comparing(e -> e.getValue().toString()));
        List<KnowledgeObject> mergedObjects = new ArrayList<>();
        for (Map.Entry<NodeId, RevisionHash> e : entries) {
            mergedObjects.add(objectFrom(e.getKey(), e.getValue()));
        }
        SnapshotRef merged = snapshot("merge:" + into.line(), mergedObjects);

        List<Link> links = new ArrayList<>();
        links.add(Link.of(FvcsVerbs.RECORDS, merged.pkg()));
        links.add(Link.of(FvcsVerbs.PARENT, ours.commit()));
        links.add(Link.of(FvcsVerbs.PARENT, theirs.commit()));
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("message", message).put("branch", into.line())
                .put("snapshot", merged.hash().toString()).put("judgment-call", true).build();
        KnowledgeObject mergeCommit = kg.define(FvcsTypes.COMMIT, payload, links.toArray(new Link[0]));
        kernel.append(org, new AppendCommand.RepointName(into.name(),
                mergeCommit.address(), ours.commit().address()), actor);

        // Advisory semantic + operational findings (Conflict Model §2): the data merged, but promotion
        // or deployment should address these first.
        List<String> semantic = semanticWarnings(d, baseMembers, oursMembers, theirsMembers);
        List<String> operational = new CompatibilityEngine(d).check(oursSnap, merged).issues();
        return new MergeResult(Optional.of(new CommitRef(mergeCommit)), List.of(), semantic, operational);
    }

    // ---- tags & compatibility ----------------------------------------------------------------

    /**
     * Tags a commit with an immovable name.
     *
     * @param name    the tag name (becomes {@code tag/<name>})
     * @param commit  the commit to mark
     * @param role    the tag role (lightweight / release / baseline)
     * @param message the tag message
     * @return the tag reference
     */
    public TagRef tag(String name, CommitRef commit, TagRole role, String message) {
        KnowledgeObject tag = kg.define(FvcsTypes.TAG,
                CanonicalValue.objectBuilder().put("name", name).put("role", role.wire())
                        .put("message", message).build(),
                Link.of(FvcsVerbs.MARKS, commit.commit()));
        Name tagName = Name.of("tag/" + name);
        kernel.append(org, new AppendCommand.RepointName(tagName, commit.commit().address(), null), actor);
        return new TagRef(tag, tagName);
    }

    /**
     * Structural compatibility: can {@code candidate} replace {@code current}?
     *
     * @param current   the required snapshot
     * @param candidate the replacement snapshot
     * @return the compatibility result
     */
    public CompatibilityResult checkCompatibility(SnapshotRef current, SnapshotRef candidate) {
        return new CompatibilityEngine(index()).check(current, candidate);
    }

    /**
     * Records a compatibility verdict as an evidenced Claim (no naked assertion — Law 5).
     *
     * @param statement  the verdict statement
     * @param method     the method that produced it
     * @param confidence calibrated confidence in [0,1]
     * @param evidence   the runs that evidence it (≥1)
     * @param from       the required snapshot
     * @param to         the candidate snapshot
     * @return the compatibility-verdict claim
     */
    public KnowledgeObject recordCompatibilityVerdict(String statement, String method, BigDecimal confidence,
                                                      List<KnowledgeObject> evidence, SnapshotRef from, SnapshotRef to) {
        List<Link> links = new ArrayList<>();
        for (KnowledgeObject run : evidence) {
            links.add(Link.of(Verbs.CITES, run));
        }
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("statement", statement).put("method", method)
                .put("confidence", CanonicalValue.of(confidence))
                .put("from", from.hash().toString()).put("to", to.hash().toString()).build();
        return kg.define(FvcsTypes.COMPATIBILITY_VERDICT, payload, links.toArray(new Link[0]));
    }

    // ---- helpers -----------------------------------------------------------------------------

    private DiffEngine index() {
        return DiffEngine.of(kernel, org, ontology);
    }

    private List<String> semanticWarnings(DiffEngine d, Map<NodeId, RevisionHash> base,
                                          Map<NodeId, RevisionHash> ours, Map<NodeId, RevisionHash> theirs) {
        // Heuristic (foundation): a belief added on the source branch may need reconciliation against the
        // target's beliefs — divergent evaluation conclusions are semantic, not structural (§2).
        List<String> out = new ArrayList<>();
        theirs.forEach((node, hash) -> {
            if (!base.containsKey(node) && !ours.containsKey(node)) {
                ObjectType t = d.typeOf(hash);
                if (t != null && t.kind() == com.broksforge.kernel.api.Kind.CLAIM) {
                    out.add("claim " + t.name() + " added on the source branch; reconcile against target beliefs");
                }
            }
        });
        return out;
    }

    private SnapshotRef snapshotForHash(DiffEngine d, RevisionHash snapshotHash) {
        NodeId node = d.nodeOf(snapshotHash);
        return new SnapshotRef(objectFrom(node, snapshotHash));
    }

    private SnapshotRef snapshotOfCommit(DiffEngine d, RevisionHash commitHash) {
        CommitRef c = new CommitRef(objectFrom(d.nodeOf(commitHash), commitHash));
        return snapshotForHash(d, c.snapshotHash());
    }

    private KnowledgeObject commitObject(Address.Revision addr) {
        Revision rev = kernel.revision(addr.revision()).orElseThrow(
                () -> new IllegalStateException("commit revision not found: " + addr.revision()));
        return new KnowledgeObject(FvcsTypes.COMMIT, addr, rev);
    }

    private KnowledgeObject objectFrom(NodeId node, RevisionHash hash) {
        if (node == null) {
            throw new IllegalStateException("no continuant known for revision " + hash);
        }
        Revision rev = kernel.revision(hash).orElseThrow(
                () -> new IllegalStateException("revision not found: " + hash));
        ObjectType type = ontology.resolve(rev.kind(), rev.subtype()).orElseThrow(
                () -> new IllegalStateException("no object type for " + rev.kind() + "/" + rev.subtype()));
        return new KnowledgeObject(type, new Address.Revision(org, rev.kind(), node, hash), rev);
    }
}
