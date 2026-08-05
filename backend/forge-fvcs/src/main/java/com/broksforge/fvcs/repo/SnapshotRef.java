package com.broksforge.fvcs.repo;

import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.knowledge.graph.KnowledgeObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A snapshot — a content-addressed manifest pinning the exact revision of every object in scope. It is
 * an {@code ArtifactPackage} (frozen ontology); its hash is the tree identity. The pinned members are the
 * package's intrinsic {@code includes} references.
 *
 * @param pkg the ArtifactPackage knowledge object
 */
public record SnapshotRef(KnowledgeObject pkg) {

    public SnapshotRef {
        if (pkg == null) {
            throw new IllegalArgumentException("snapshot package must not be null");
        }
    }

    /** @return the snapshot (tree) identity */
    public RevisionHash hash() {
        return pkg.hash();
    }

    /** @return the revision hashes this snapshot pins (its {@code includes} members) */
    public List<RevisionHash> members() {
        List<RevisionHash> out = new ArrayList<>();
        for (Ref r : pkg.revision().refs()) {
            if (r.verb().name().equals("includes")) {
                out.add(r.target());
            }
        }
        return out;
    }
}
