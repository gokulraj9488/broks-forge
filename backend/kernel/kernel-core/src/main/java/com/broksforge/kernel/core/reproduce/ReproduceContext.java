package com.broksforge.kernel.core.reproduce;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;

import java.util.Map;

/**
 * Everything a {@link Reproducer} needs to re-execute a revision: the revision itself, its hash, its
 * pinned composition closure, and where it lives. The closure is what makes reproduction meaningful —
 * it is the exact configuration the revision runs under (ADR-V2-0005).
 *
 * @param org          the organization
 * @param node         the continuant being reproduced
 * @param revisionHash the revision's content hash
 * @param revision     the revision
 * @param closure      the composition closure (root first), pinned by hash
 */
public record ReproduceContext(
        OrgId org,
        NodeId node,
        RevisionHash revisionHash,
        Revision revision,
        Map<RevisionHash, Revision> closure) {

    public ReproduceContext {
        if (org == null || node == null || revisionHash == null || revision == null || closure == null) {
            throw new IllegalArgumentException("reproduce context fields must not be null");
        }
        closure = Map.copyOf(closure);
    }
}
