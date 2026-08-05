package com.broksforge.kernel.core.store;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.op.Edge;

import java.util.List;
import java.util.Optional;

/**
 * The graph adjacency projection that powers {@code traverse} — a rebuildable view of the log.
 *
 * <p>Folding {@code NodePut} facts records each continuant's kind and revision history and projects
 * the revision's intrinsic {@link com.broksforge.kernel.api.Ref}s as edges; folding edge facts
 * records extrinsic edges and their retractions. It holds no truth of its own.
 */
public interface GraphIndex {

    /**
     * Folds one committed entry into the projection.
     *
     * @param entry the entry
     */
    void apply(LogEntry entry);

    /**
     * @param org  the organization
     * @param node the continuant
     * @return the continuant's kind, if it exists
     */
    Optional<Kind> kindOf(OrgId org, NodeId node);

    /**
     * @param org  the organization
     * @param node the continuant
     * @return the continuant's revision hashes in append order (empty if unknown)
     */
    List<RevisionHash> revisionsOf(OrgId org, NodeId node);

    /**
     * @param org  the organization
     * @param hash a revision hash
     * @return the address under which this revision was first recorded, if known
     */
    Optional<Address.Revision> addressOf(OrgId org, RevisionHash hash);

    /**
     * @param org  the organization
     * @param from the source address
     * @return the currently-live outgoing edges (intrinsic + non-retracted extrinsic)
     */
    List<Edge> outEdges(OrgId org, Address from);

    /**
     * @param org the organization
     * @param to  the target address
     * @return the currently-live incoming edges (intrinsic + non-retracted extrinsic)
     */
    List<Edge> inEdges(OrgId org, Address to);
}
