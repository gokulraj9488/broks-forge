package com.broksforge.kernel.core.op;

import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.store.RevisionStore;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computes the composition closure of a revision (ADR-V2-0005): the transitive set of revisions
 * reachable by following {@link EdgeFamily#COMPOSITION} references.
 *
 * <p>Because a revision's content hash Merkle-covers its references, the <b>root revision hash is
 * itself the closure certificate</b> — it already commits to the entire closure. This engine's job
 * is therefore enumeration (for inspection and architecture diff), not identity. The walk is
 * guaranteed to terminate: a reference can only target an already-existing hash, so the composition
 * graph is acyclic by construction; a visited set guards against re-expansion regardless.
 */
public final class ClosureEngine {

    private final RevisionStore revisions;

    public ClosureEngine(RevisionStore revisions) {
        this.revisions = revisions;
    }

    /**
     * @param root the root revision hash
     * @return an ordered map (root first) of every revision in the composition closure
     * @throws IllegalArgumentException if the root revision is not present in the store
     */
    public Map<RevisionHash, Revision> closure(RevisionHash root) {
        Revision rootRevision = revisions.get(root).orElseThrow(
                () -> new IllegalArgumentException("root revision not found: " + root));

        Map<RevisionHash, Revision> result = new LinkedHashMap<>();
        result.put(root, rootRevision);

        Deque<RevisionHash> frontier = new ArrayDeque<>();
        frontier.add(root);
        while (!frontier.isEmpty()) {
            Revision current = result.get(frontier.removeFirst());
            for (Ref ref : current.refs()) {
                if (ref.family() != EdgeFamily.COMPOSITION) {
                    continue;
                }
                RevisionHash target = ref.target();
                if (result.containsKey(target)) {
                    continue;
                }
                Revision child = revisions.get(target).orElseThrow(
                        () -> new IllegalStateException("dangling composition reference: " + target));
                result.put(target, child);
                frontier.add(target);
            }
        }
        return result;
    }
}
