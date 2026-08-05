package com.broksforge.fvcs.repo;

import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A commit — an act of will checkpointing a snapshot on a branch. It wraps the {@code Commit} Decision
 * knowledge object; its message, recorded snapshot, and parent commit(s) are read from the Decision's
 * payload and intrinsic references. A commit with ≥2 parents is a merge commit.
 *
 * @param commit the Commit knowledge object
 */
public record CommitRef(KnowledgeObject commit) {

    public CommitRef {
        if (commit == null) {
            throw new IllegalArgumentException("commit must not be null");
        }
    }

    /** @return the commit's content identity */
    public RevisionHash hash() {
        return commit.hash();
    }

    /** @return the commit message */
    public String message() {
        return string("message").orElse("");
    }

    /** @return the recorded snapshot (tree) hash */
    public RevisionHash snapshotHash() {
        for (Ref r : commit.revision().refs()) {
            if (r.verb().name().equals("records")) {
                return r.target();
            }
        }
        throw new IllegalStateException("commit records no snapshot: " + hash());
    }

    /** @return the parent commit hashes (empty for a root commit; ≥2 for a merge) */
    public List<RevisionHash> parents() {
        List<RevisionHash> out = new ArrayList<>();
        for (Ref r : commit.revision().refs()) {
            if (r.verb().name().equals("parent")) {
                out.add(r.target());
            }
        }
        return out;
    }

    /** @return true if this commit has two or more parents */
    public boolean isMerge() {
        return parents().size() >= 2;
    }

    private Optional<String> string(String key) {
        if (commit.payload() instanceof CanonicalValue.Obj o
                && o.entries().get(key) instanceof CanonicalValue.Str s) {
            return Optional.of(s.value());
        }
        return Optional.empty();
    }
}
