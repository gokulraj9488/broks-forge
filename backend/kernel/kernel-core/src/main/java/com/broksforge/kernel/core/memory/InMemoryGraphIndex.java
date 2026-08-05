package com.broksforge.kernel.core.memory;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.kernel.core.op.Edge;
import com.broksforge.kernel.core.store.GraphIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory {@link GraphIndex}: adjacency and node metadata folded from the log. Intrinsic edges are
 * projected from revision content at {@code NodePut} time; extrinsic edges and their retractions come
 * from edge facts. All access is guarded by the instance monitor — a coarse but correct choice for
 * the reference backend (readers never see a partially applied fold).
 */
public final class InMemoryGraphIndex implements GraphIndex {

    private record Scope(OrgId org) {
    }

    private final Map<Scope, Map<NodeId, Kind>> kindByNode = new HashMap<>();
    private final Map<Scope, Map<NodeId, List<RevisionHash>>> revsByNode = new HashMap<>();
    private final Map<Scope, Map<RevisionHash, Address.Revision>> addrByRev = new HashMap<>();
    private final Map<Scope, Map<String, List<Edge>>> out = new HashMap<>();
    private final Map<Scope, Map<String, List<Edge>>> in = new HashMap<>();
    private final Map<Scope, Set<String>> retracted = new HashMap<>();

    @Override
    public synchronized void apply(LogEntry entry) {
        Scope s = new Scope(entry.org());
        switch (entry.payload()) {
            case Payload.NodePut np -> applyNodePut(s, entry.org(), np.node(), np.revision());
            case Payload.EdgeAsserted ea -> {
                Edge e = new Edge(ea.edge().from(), ea.edge().verb(), ea.edge().to(), false);
                addEdge(s, e);
                Set<String> dead = retracted.get(s);
                if (dead != null) {
                    dead.remove(sig(e)); // re-asserting revives a previously retracted edge
                }
            }
            case Payload.EdgeRetracted er -> {
                Edge e = new Edge(er.edge().from(), er.edge().verb(), er.edge().to(), false);
                retracted.computeIfAbsent(s, k -> new HashSet<>()).add(sig(e));
            }
            case Payload.NameRepointed ignored -> {
                // names are the NameStore's concern
            }
            case Payload.ClockTick ignored -> {
                // ticks carry no graph structure
            }
        }
    }

    private void applyNodePut(Scope s, OrgId org, NodeId node, Revision rev) {
        RevisionHash hash = rev.hash();
        kindByNode.computeIfAbsent(s, k -> new HashMap<>()).putIfAbsent(node, rev.kind());
        revsByNode.computeIfAbsent(s, k -> new HashMap<>())
                .computeIfAbsent(node, n -> new ArrayList<>()).add(hash);
        Address.Revision self = new Address.Revision(org, rev.kind(), node, hash);
        addrByRev.computeIfAbsent(s, k -> new HashMap<>()).putIfAbsent(hash, self);

        Map<RevisionHash, Address.Revision> addr = addrByRev.get(s);
        for (Ref ref : rev.refs()) {
            Address.Revision target = addr.get(ref.target());
            if (target != null) {
                addEdge(s, new Edge(self, ref.verb(), target, true));
            }
        }
    }

    private void addEdge(Scope s, Edge e) {
        out.computeIfAbsent(s, k -> new HashMap<>())
                .computeIfAbsent(uri(e.from()), u -> new ArrayList<>()).add(e);
        in.computeIfAbsent(s, k -> new HashMap<>())
                .computeIfAbsent(uri(e.to()), u -> new ArrayList<>()).add(e);
    }

    @Override
    public synchronized Optional<Kind> kindOf(OrgId org, NodeId node) {
        return Optional.ofNullable(kindByNode.getOrDefault(new Scope(org), Map.of()).get(node));
    }

    @Override
    public synchronized List<RevisionHash> revisionsOf(OrgId org, NodeId node) {
        return List.copyOf(revsByNode.getOrDefault(new Scope(org), Map.of())
                .getOrDefault(node, List.of()));
    }

    @Override
    public synchronized Optional<Address.Revision> addressOf(OrgId org, RevisionHash hash) {
        return Optional.ofNullable(addrByRev.getOrDefault(new Scope(org), Map.of()).get(hash));
    }

    @Override
    public synchronized List<Edge> outEdges(OrgId org, Address from) {
        return liveEdges(new Scope(org), out, uri(from));
    }

    @Override
    public synchronized List<Edge> inEdges(OrgId org, Address to) {
        return liveEdges(new Scope(org), in, uri(to));
    }

    private List<Edge> liveEdges(Scope s, Map<Scope, Map<String, List<Edge>>> side, String key) {
        Set<String> dead = retracted.getOrDefault(s, Set.of());
        List<Edge> result = new ArrayList<>();
        for (Edge e : side.getOrDefault(s, Map.of()).getOrDefault(key, List.of())) {
            if (e.intrinsic() || !dead.contains(sig(e))) {
                result.add(e);
            }
        }
        return result;
    }

    private static String uri(Address a) {
        return a.toUri();
    }

    private static String sig(Edge e) {
        return e.from().toUri() + "|" + e.verb().name() + "|" + e.to().toUri();
    }
}
