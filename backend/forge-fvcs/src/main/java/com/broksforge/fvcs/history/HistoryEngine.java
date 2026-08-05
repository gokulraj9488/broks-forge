package com.broksforge.fvcs.history;

import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The version graph — the commit DAG folded from the append log, and the graph algorithms merge relies
 * on (ancestors, and the lowest-common-ancestor merge base). Pure projection: deterministic, rebuildable,
 * never separate mutable storage.
 */
public final class HistoryEngine {

    private final Map<RevisionHash, CommitNode> commits = new LinkedHashMap<>();

    private HistoryEngine() {
    }

    /**
     * Folds an organization's log into the commit DAG.
     *
     * @param kernel the kernel
     * @param org    the organization
     * @return the history engine over that org's commits
     */
    public static HistoryEngine of(ForgeKernel kernel, OrgId org) {
        HistoryEngine h = new HistoryEngine();
        for (LogEntry entry : kernel.log(org)) {
            if (entry.payload() instanceof Payload.NodePut np) {
                Revision rev = np.revision();
                if (!rev.subtype().equals("commit")) {
                    continue;
                }
                List<RevisionHash> parents = new ArrayList<>();
                RevisionHash snapshot = null;
                for (Ref r : rev.refs()) {
                    switch (r.verb().name()) {
                        case "parent" -> parents.add(r.target());
                        case "records" -> snapshot = r.target();
                        default -> { /* other refs ignored by history */ }
                    }
                }
                String message = rev.payload() instanceof CanonicalValue.Obj o
                        && o.entries().get("message") instanceof CanonicalValue.Str s ? s.value() : "";
                h.commits.put(rev.hash(), new CommitNode(rev.hash(), parents, snapshot, message,
                        entry.provenance().actor(), entry.position()));
            }
        }
        return h;
    }

    /**
     * @param hash a commit hash
     * @return the commit node, if present
     */
    public Optional<CommitNode> node(RevisionHash hash) {
        return Optional.ofNullable(commits.get(hash));
    }

    /**
     * @param head a commit hash
     * @return that commit and all its ancestors, newest first (by log position)
     */
    public List<CommitNode> history(RevisionHash head) {
        List<CommitNode> out = new ArrayList<>();
        for (RevisionHash h : ancestorsInclusive(head)) {
            CommitNode n = commits.get(h);
            if (n != null) {
                out.add(n);
            }
        }
        out.sort((a, b) -> Long.compare(b.position().value(), a.position().value()));
        return out;
    }

    /**
     * @param commit a commit hash
     * @return the set of the commit and all ancestors (following {@code parent} edges)
     */
    public Set<RevisionHash> ancestorsInclusive(RevisionHash commit) {
        Set<RevisionHash> seen = new LinkedHashSet<>();
        Deque<RevisionHash> stack = new ArrayDeque<>();
        stack.push(commit);
        while (!stack.isEmpty()) {
            RevisionHash h = stack.pop();
            if (!seen.add(h)) {
                continue;
            }
            CommitNode n = commits.get(h);
            if (n != null) {
                for (RevisionHash p : n.parents()) {
                    if (!seen.contains(p)) {
                        stack.push(p);
                    }
                }
            }
        }
        return seen;
    }

    /**
     * Finds the merge base (lowest common ancestor) of two commits. Deterministic: the LCA set is the
     * maximal common ancestors; a single element is the normal case, more than one is criss-cross.
     *
     * @param a one commit hash
     * @param b the other commit hash
     * @return the merge base result
     */
    public MergeBase mergeBase(RevisionHash a, RevisionHash b) {
        Set<RevisionHash> ancA = ancestorsInclusive(a);
        Set<RevisionHash> ancB = ancestorsInclusive(b);
        Set<RevisionHash> common = new LinkedHashSet<>(ancA);
        common.retainAll(ancB);
        if (common.isEmpty()) {
            return new MergeBase(MergeBase.Kind.NONE, List.of());
        }
        // An LCA is a common ancestor that is not a proper ancestor of any other common ancestor
        // (i.e. none of its descendants is also a common ancestor — it is "lowest").
        List<RevisionHash> lcas = new ArrayList<>();
        for (RevisionHash c : common) {
            boolean lowest = true;
            for (RevisionHash other : common) {
                if (!other.equals(c) && ancestorsInclusive(other).contains(c)) {
                    lowest = false; // c is an ancestor of a lower common commit
                    break;
                }
            }
            if (lowest) {
                lcas.add(c);
            }
        }
        // Order deterministically by position (newest first).
        lcas.sort((x, y) -> Long.compare(pos(y), pos(x)));
        if (lcas.size() == 1) {
            return new MergeBase(MergeBase.Kind.SINGLE, lcas);
        }
        return new MergeBase(MergeBase.Kind.CRISS_CROSS, lcas);
    }

    /** @return all commit nodes, newest first */
    public List<CommitNode> all() {
        List<CommitNode> out = new ArrayList<>(commits.values());
        out.sort((a, b) -> Long.compare(b.position().value(), a.position().value()));
        return out;
    }

    private long pos(RevisionHash h) {
        CommitNode n = commits.get(h);
        return n == null ? -1 : n.position().value();
    }
}
