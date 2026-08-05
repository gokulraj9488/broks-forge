package com.broksforge.fvcs.merge;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.RevisionHash;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The three-way merge computation (Conflict Model §1). Pure and deterministic: it takes the base, ours,
 * and theirs member maps (continuant → pinned revision) and produces either the reconciled member set or
 * the structural conflicts that block the merge. Semantic and operational findings are computed by the
 * repository around this engine (they concern *using* the merge, not recording it).
 *
 * <p>Continuant identity is globally unique (node ids are minted per creation), so an "add/add" of the
 * same continuant on two branches cannot occur; independent adds are different continuants and merge as a
 * union. The structural conflicts that remain are therefore modify/modify and modify/remove.
 */
public final class MergeEngine {

    private MergeEngine() {
    }

    /**
     * @param base        the merge-base members (empty for unrelated histories)
     * @param ours        the target-branch members
     * @param theirs      the source-branch members
     * @param resolutions caller-supplied resolutions (continuant → chosen revision) for conflicts
     * @return the merge plan (conflicts, or the reconciled member set)
     */
    public static MergePlan plan(Map<NodeId, RevisionHash> base,
                                 Map<NodeId, RevisionHash> ours,
                                 Map<NodeId, RevisionHash> theirs,
                                 Map<NodeId, RevisionHash> resolutions) {
        Set<NodeId> nodes = new LinkedHashSet<>();
        nodes.addAll(base.keySet());
        nodes.addAll(ours.keySet());
        nodes.addAll(theirs.keySet());

        List<Conflict> conflicts = new ArrayList<>();
        Map<NodeId, RevisionHash> merged = new LinkedHashMap<>();

        for (NodeId node : nodes) {
            RevisionHash b = base.get(node);
            RevisionHash o = ours.get(node);
            RevisionHash t = theirs.get(node);

            if (o != null && t != null) {                       // present on both sides
                boolean oursChanged = !Objects.equals(o, b);
                boolean theirsChanged = !Objects.equals(t, b);
                if (!oursChanged && !theirsChanged) {
                    merged.put(node, o);
                } else if (oursChanged && !theirsChanged) {
                    merged.put(node, o);
                } else if (!oursChanged) {
                    merged.put(node, t);
                } else if (Objects.equals(o, t)) {
                    merged.put(node, o);                        // both made the same change
                } else {
                    resolveOrConflict(conflicts, merged, resolutions, node,
                            Conflict.structural(ConflictKind.MODIFY_MODIFY, node, b, o, t,
                                    "the same object changed to different revisions on both sides"));
                }
            } else if (o != null) {                              // theirs absent
                if (Objects.equals(o, b)) {
                    // theirs removed, ours unchanged → remove (drop)
                } else if (b == null) {
                    merged.put(node, o);                        // ours added
                } else {
                    resolveOrConflict(conflicts, merged, resolutions, node,
                            Conflict.structural(ConflictKind.MODIFY_REMOVE, node, b, o, null,
                                    "ours modified an object the other side removed"));
                }
            } else if (t != null) {                              // ours absent
                if (Objects.equals(t, b)) {
                    // ours removed, theirs unchanged → remove (drop)
                } else if (b == null) {
                    merged.put(node, t);                        // theirs added
                } else {
                    resolveOrConflict(conflicts, merged, resolutions, node,
                            Conflict.structural(ConflictKind.MODIFY_REMOVE, node, b, null, t,
                                    "theirs modified an object ours removed"));
                }
            }
            // both absent → both removed → drop
        }
        return new MergePlan(conflicts, merged);
    }

    private static void resolveOrConflict(List<Conflict> conflicts, Map<NodeId, RevisionHash> merged,
                                          Map<NodeId, RevisionHash> resolutions, NodeId node, Conflict conflict) {
        RevisionHash resolved = resolutions.get(node);
        if (resolved != null) {
            merged.put(node, resolved);
        } else {
            conflicts.add(conflict);
        }
    }
}
