package com.broksforge.explorer.graph;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A read-only projection of one organization's engineering graph, folded from the append log.
 *
 * <p>The Forge Kernel exposes {@code resolve}, {@code traverse}, and {@code closure}, but every one of
 * those needs an address the caller already holds; there is no public way to <em>enumerate</em> the
 * graph — to ask "what nodes exist?", "what names are defined?", "show me everything". A graph explorer
 * needs exactly that, so it must do what the kernel already does internally: replay {@code log(org)}
 * and fold the payloads back into node, edge, and name projections. This class is that fold.
 *
 * <p>It is intentionally the one place in the application that reconstructs kernel-side projections
 * from first principles, and it is cited in the usability report as the clearest "read-side
 * enumeration" friction point.
 */
public final class GraphModel {

    /** A node continuant with its revision history. */
    public record Node(NodeId id, Kind kind, List<Revision> revisions) {
        /** @return the most recent revision */
        public Revision latest() {
            return revisions.get(revisions.size() - 1);
        }
    }

    /** A directed relationship in the folded graph. */
    public record Relationship(Address from, Verb verb, Address to, boolean intrinsic) {
    }

    private final Map<NodeId, List<Revision>> revisionsByNode = new LinkedHashMap<>();
    private final Map<NodeId, Kind> kindByNode = new LinkedHashMap<>();
    private final Map<RevisionHash, Address.Revision> addressByRevision = new LinkedHashMap<>();
    private final List<Relationship> intrinsic = new ArrayList<>();
    private final Set<EdgeKey> extrinsic = new LinkedHashSet<>();
    private final Map<String, Address.Revision> names = new LinkedHashMap<>();
    private int ticks;

    private GraphModel() {
    }

    /**
     * Folds the entire log of an organization into a graph model.
     *
     * @param kernel the kernel
     * @param org    the organization
     * @return the folded model
     */
    public static GraphModel of(ForgeKernel kernel, OrgId org) {
        GraphModel m = new GraphModel();
        for (LogEntry entry : kernel.log(org)) {
            switch (entry.payload()) {
                case Payload.NodePut np -> m.applyPut(org, np);
                case Payload.EdgeAsserted ea -> m.extrinsic.add(ea.edge());
                case Payload.EdgeRetracted er -> m.extrinsic.remove(er.edge());
                case Payload.NameRepointed nr -> m.names.put(nr.name().path(), nr.to());
                case Payload.ClockTick ignored -> m.ticks++;
            }
        }
        return m;
    }

    private void applyPut(OrgId org, Payload.NodePut np) {
        NodeId node = np.node();
        Revision revision = np.revision();
        revisionsByNode.computeIfAbsent(node, n -> new ArrayList<>()).add(revision);
        kindByNode.putIfAbsent(node, revision.kind());
        Address.Revision address = new Address.Revision(org, revision.kind(), node, revision.hash());
        addressByRevision.put(revision.hash(), address);
        for (Ref ref : revision.refs()) {
            Address target = addressByRevision.getOrDefault(ref.target(),
                    // A ref may point at content whose owning node we have not folded yet in edge form;
                    // fall back to a bare revision reference the DOT/ASCII renderers can still show.
                    new Address.Revision(org, revision.kind(), node, ref.target()));
            intrinsic.add(new Relationship(address, ref.verb(), target, true));
        }
    }

    /** @return all nodes, in creation order */
    public List<Node> nodes() {
        List<Node> out = new ArrayList<>();
        revisionsByNode.forEach((id, revs) -> out.add(new Node(id, kindByNode.get(id), List.copyOf(revs))));
        return out;
    }

    /**
     * @param kind a kind
     * @return the nodes of that kind
     */
    public List<Node> nodesOfKind(Kind kind) {
        return nodes().stream().filter(n -> n.kind() == kind).toList();
    }

    /** @return the intrinsic relationships (projected from revision refs) */
    public List<Relationship> intrinsicEdges() {
        return List.copyOf(intrinsic);
    }

    /** @return the currently-active extrinsic edges (asserted and not retracted) */
    public List<Relationship> extrinsicEdges() {
        List<Relationship> out = new ArrayList<>();
        for (EdgeKey e : extrinsic) {
            out.add(new Relationship(e.from(), e.verb(), e.to(), false));
        }
        return out;
    }

    /** @return all relationships, intrinsic then extrinsic */
    public List<Relationship> allEdges() {
        List<Relationship> out = new ArrayList<>(intrinsicEdges());
        out.addAll(extrinsicEdges());
        return out;
    }

    /** @return the current name pointers, by path */
    public Map<String, Address.Revision> names() {
        return Map.copyOf(names);
    }

    /**
     * @param hash a revision hash
     * @return the address for that revision, if this model has folded it
     */
    public Optional<Address.Revision> addressOf(RevisionHash hash) {
        return Optional.ofNullable(addressByRevision.get(hash));
    }

    /** @return the number of clock ticks folded */
    public int tickCount() {
        return ticks;
    }
}
