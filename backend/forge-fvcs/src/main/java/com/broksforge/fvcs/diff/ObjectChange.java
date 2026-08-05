package com.broksforge.fvcs.diff;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.knowledge.ontology.ObjectType;

/**
 * A per-continuant change between two snapshots: which object, of what type, how it changed, and its
 * before/after pinned revisions. The unit is the continuant ({@link NodeId}) — the same thing changed to
 * a different revision is a change; a different thing is an add/remove.
 *
 * @param node   the continuant
 * @param type   its object type (may be null if unknown to the ontology)
 * @param kind   the change kind
 * @param before the revision pinned by the left snapshot, or null if absent (ADDED)
 * @param after  the revision pinned by the right snapshot, or null if absent (REMOVED)
 */
public record ObjectChange(NodeId node, ObjectType type, ChangeKind kind,
                           RevisionHash before, RevisionHash after) {
}
