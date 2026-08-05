package com.broksforge.fvcs.diff;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.knowledge.ontology.ObjectType;
import com.broksforge.knowledge.ontology.Ontology;
import com.broksforge.fvcs.repo.SnapshotRef;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes change sets between snapshots. Snapshots pin <em>revisions</em>; the diff is expressed per
 * <em>continuant</em>, so the engine holds an index from revision hash to its owning {@link NodeId} and
 * {@link ObjectType}, folded once from the log. Diffing is then a set comparison over pinned members.
 */
public final class DiffEngine {

    private final Map<RevisionHash, NodeId> nodeOf;
    private final Map<RevisionHash, ObjectType> typeOf;

    private DiffEngine(Map<RevisionHash, NodeId> nodeOf, Map<RevisionHash, ObjectType> typeOf) {
        this.nodeOf = nodeOf;
        this.typeOf = typeOf;
    }

    /**
     * @param kernel   the kernel
     * @param org      the organization
     * @param ontology the ontology used to type members
     * @return a diff engine indexed over the org's revisions
     */
    public static DiffEngine of(ForgeKernel kernel, OrgId org, Ontology ontology) {
        Map<RevisionHash, NodeId> nodeOf = new LinkedHashMap<>();
        Map<RevisionHash, ObjectType> typeOf = new LinkedHashMap<>();
        for (LogEntry entry : kernel.log(org)) {
            if (entry.payload() instanceof Payload.NodePut np) {
                Revision rev = np.revision();
                nodeOf.put(rev.hash(), np.node());
                ontology.resolve(rev.kind(), rev.subtype()).ifPresent(t -> typeOf.put(rev.hash(), t));
            }
        }
        return new DiffEngine(nodeOf, typeOf);
    }

    /**
     * The pinned members of a snapshot, keyed by continuant (so "same thing, new revision" is detectable).
     *
     * @param snapshot a snapshot
     * @return a map from continuant to the revision the snapshot pins
     */
    public Map<NodeId, RevisionHash> members(SnapshotRef snapshot) {
        Map<NodeId, RevisionHash> out = new LinkedHashMap<>();
        for (RevisionHash member : snapshot.members()) {
            NodeId node = nodeOf.get(member);
            if (node != null) {
                out.put(node, member);
            }
        }
        return out;
    }

    /**
     * The two-way diff between a left (old) and right (new) snapshot.
     *
     * @param left  the left snapshot
     * @param right the right snapshot
     * @return the change set
     */
    public ChangeSet diff(SnapshotRef left, SnapshotRef right) {
        Map<NodeId, RevisionHash> a = members(left);
        Map<NodeId, RevisionHash> b = members(right);
        Set<NodeId> nodes = new LinkedHashSet<>();
        nodes.addAll(a.keySet());
        nodes.addAll(b.keySet());

        java.util.List<ObjectChange> changes = new java.util.ArrayList<>();
        for (NodeId node : nodes) {
            RevisionHash before = a.get(node);
            RevisionHash after = b.get(node);
            ChangeKind kind;
            if (before == null) {
                kind = ChangeKind.ADDED;
            } else if (after == null) {
                kind = ChangeKind.REMOVED;
            } else if (before.equals(after)) {
                kind = ChangeKind.UNCHANGED;
            } else {
                kind = ChangeKind.CHANGED;
            }
            if (kind != ChangeKind.UNCHANGED) {
                changes.add(new ObjectChange(node, typeOf.get(after != null ? after : before),
                        kind, before, after));
            }
        }
        return new ChangeSet(List.copyOf(changes));
    }

    /** @param hash a revision hash @return its owning continuant, if indexed */
    public NodeId nodeOf(RevisionHash hash) {
        return nodeOf.get(hash);
    }

    /** @param hash a revision hash @return its object type, if indexed */
    public ObjectType typeOf(RevisionHash hash) {
        return typeOf.get(hash);
    }
}
