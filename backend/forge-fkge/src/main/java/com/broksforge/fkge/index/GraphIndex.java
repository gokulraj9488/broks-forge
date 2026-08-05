package com.broksforge.fkge.index;

import com.broksforge.fvcs.repo.Repository;
import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.knowledge.ontology.ObjectType;
import com.broksforge.knowledge.ontology.Ontology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one projection. FKGE folds {@code kernel.log(org)} exactly once into an immutable, typed adjacency
 * structure — a deterministic function of the log, fully discardable, with no hidden mutable state.
 *
 * <p>Built entirely through public APIs: {@link ForgeKernel#log}, {@link Revision#refs()},
 * {@link Ontology#resolve}. Intrinsic edges come from each node's latest revision refs (targets resolved
 * from revision hash to owning continuant); extrinsic edges are the live asserted-minus-retracted set.
 *
 * <p>{@link #of} folds the whole log (latest state); {@link #asOf} folds only entries at or before a
 * {@link LogPosition} (deterministic time travel).
 */
public final class GraphIndex {

    private final ForgeKernel kernel;
    private final com.broksforge.kernel.api.OrgId org;
    private final Ontology ontology;
    private final Map<NodeId, GraphNode> nodes;
    private final Map<RevisionHash, NodeId> nodeOfRevision;
    private final Map<NodeId, List<GraphEdge>> outEdges;
    private final Map<NodeId, List<GraphEdge>> inEdges;
    private final LogPosition position; // effective as-of (max folded position)

    private GraphIndex(ForgeKernel kernel, com.broksforge.kernel.api.OrgId org, Ontology ontology,
                       Map<NodeId, GraphNode> nodes, Map<RevisionHash, NodeId> nodeOfRevision,
                       Map<NodeId, List<GraphEdge>> outEdges, Map<NodeId, List<GraphEdge>> inEdges,
                       LogPosition position) {
        this.kernel = kernel;
        this.org = org;
        this.ontology = ontology;
        this.nodes = nodes;
        this.nodeOfRevision = nodeOfRevision;
        this.outEdges = outEdges;
        this.inEdges = inEdges;
        this.position = position;
    }

    /** Fold the repository's org log at latest state. */
    public static GraphIndex of(Repository repo) {
        return build(repo.kernel(), repo.org(), repo.ontology(), null);
    }

    /** Fold an org log at latest state, over a supplied (typically composed) ontology. */
    public static GraphIndex of(ForgeKernel kernel, com.broksforge.kernel.api.OrgId org, Ontology ontology) {
        return build(kernel, org, ontology, null);
    }

    /** Fold only entries at or before {@code asOf} — deterministic reconstruction of a past state. */
    public static GraphIndex asOf(ForgeKernel kernel, com.broksforge.kernel.api.OrgId org, Ontology ontology, LogPosition asOf) {
        if (asOf == null) throw new IllegalArgumentException("asOf");
        return build(kernel, org, ontology, asOf);
    }

    private static GraphIndex build(ForgeKernel kernel, com.broksforge.kernel.api.OrgId org, Ontology ontology, LogPosition asOf) {
        if (kernel == null || org == null || ontology == null) throw new IllegalArgumentException("null argument");

        Map<NodeId, GraphNode> nodes = new LinkedHashMap<>();
        Map<NodeId, List<Ref>> latestRefs = new LinkedHashMap<>();
        Map<RevisionHash, NodeId> nodeOfRevision = new LinkedHashMap<>();
        LogPosition maxPos = LogPosition.ZERO;

        List<LogEntry> log = kernel.log(org);

        // Pass 1 — nodes (last NodePut wins = latest revision) and revision-hash -> continuant resolution.
        for (LogEntry e : log) {
            if (asOf != null && e.position().compareTo(asOf) > 0) continue;
            if (e.position().compareTo(maxPos) > 0) maxPos = e.position();
            if (e.payload() instanceof Payload.NodePut np) {
                Revision rev = np.revision();
                RevisionHash h = rev.hash();
                nodeOfRevision.put(h, np.node());
                ObjectType type = ontology.resolve(rev.kind(), rev.subtype()).orElse(null);
                nodes.put(np.node(), new GraphNode(np.node(), rev.kind(), rev.subtype(), type, h,
                        e.position(), e.provenance().actor(), rev.payload()));
                latestRefs.put(np.node(), rev.refs());
            }
        }

        Map<NodeId, List<GraphEdge>> out = new LinkedHashMap<>();
        Map<NodeId, List<GraphEdge>> in = new LinkedHashMap<>();

        // Pass 2 — intrinsic edges from each node's latest revision refs.
        for (GraphNode n : nodes.values()) {
            for (Ref ref : latestRefs.getOrDefault(n.id(), List.of())) {
                NodeId target = nodeOfRevision.get(ref.target());
                if (target == null) continue; // dangling ref: surfaced as incompleteness, never silently traversed
                addEdge(out, in, new GraphEdge(n.id(), ref.verb(), ref.verb().family(), target, true, n.position()));
            }
        }

        // Pass 3 — extrinsic edges: asserted minus retracted, in log order.
        Map<String, GraphEdge> live = new LinkedHashMap<>();
        for (LogEntry e : log) {
            if (asOf != null && e.position().compareTo(asOf) > 0) continue;
            if (e.payload() instanceof Payload.EdgeAsserted ea) {
                EdgeKey k = ea.edge();
                NodeId f = nodeIdOf(k.from());
                NodeId t = nodeIdOf(k.to());
                if (f == null || t == null) continue;
                live.put(keyOf(k), new GraphEdge(f, k.verb(), k.verb().family(), t, false, e.position()));
            } else if (e.payload() instanceof Payload.EdgeRetracted er) {
                live.remove(keyOf(er.edge()));
            }
        }
        for (GraphEdge edge : live.values()) {
            addEdge(out, in, edge);
        }

        out.replaceAll((k, v) -> sortedCopy(v));
        in.replaceAll((k, v) -> sortedCopy(v));

        return new GraphIndex(kernel, org, ontology, Map.copyOf(nodes), Map.copyOf(nodeOfRevision),
                Map.copyOf(out), Map.copyOf(in), maxPos);
    }

    private static void addEdge(Map<NodeId, List<GraphEdge>> out, Map<NodeId, List<GraphEdge>> in, GraphEdge edge) {
        out.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
        in.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
    }

    private static List<GraphEdge> sortedCopy(List<GraphEdge> edges) {
        List<GraphEdge> copy = new ArrayList<>(edges);
        copy.sort(Order.EDGES);
        return List.copyOf(copy);
    }

    private static NodeId nodeIdOf(Address address) {
        return switch (address) {
            case Address.Node n -> n.node();
            case Address.Revision r -> r.node();
            case Address.NamePointer p -> null;
        };
    }

    private static String keyOf(EdgeKey k) {
        return k.from().toUri() + "|" + k.verb().name() + "|" + k.to().toUri();
    }

    // ---- Read surface (all deterministic, all immutable views) ----

    public Optional<GraphNode> node(NodeId id) {
        return Optional.ofNullable(nodes.get(id));
    }

    /** All nodes, in the kernel total order. */
    public List<GraphNode> nodes() {
        List<GraphNode> all = new ArrayList<>(nodes.values());
        all.sort(Order.NODES);
        return List.copyOf(all);
    }

    public List<GraphNode> nodesOfKind(Kind kind) {
        return nodes().stream().filter(n -> n.kind() == kind).toList();
    }

    public List<GraphNode> nodesOfType(ObjectType type) {
        return nodes().stream().filter(n -> type.equals(n.type())).toList();
    }

    /** Outgoing edges of {@code id} (toward what it rests on), pre-sorted. */
    public List<GraphEdge> out(NodeId id) {
        return outEdges.getOrDefault(id, List.of());
    }

    /** Incoming edges of {@code id} (things that rest on it), pre-sorted. */
    public List<GraphEdge> in(NodeId id) {
        return inEdges.getOrDefault(id, List.of());
    }

    public Optional<NodeId> resolveRevision(RevisionHash hash) {
        return Optional.ofNullable(nodeOfRevision.get(hash));
    }

    /** The effective log position this index was folded at — the {@code asOf} every answer cites. */
    public LogPosition position() {
        return position;
    }

    public Ontology ontology() {
        return ontology;
    }

    public com.broksforge.kernel.api.OrgId org() {
        return org;
    }

    public ForgeKernel kernel() {
        return kernel;
    }
}
