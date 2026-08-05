package com.broksforge.fkge.search;

import com.broksforge.fkge.index.GraphEdge;
import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.index.Order;
import com.broksforge.kernel.api.NodeId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structural, deterministic similarity — never learned or probabilistic. Two nodes are similar iff they
 * share an object type and have an isomorphic typed one-step neighborhood, compared by a
 * <em>neighborhood signature</em>: a content hash of the sorted multiset of {@code (direction, family,
 * neighbor label)} triples. Embeddings/vector similarity are rejected as opaque and non-reproducible.
 */
public final class SearchEngine {

    private final GraphIndex index;

    public SearchEngine(GraphIndex index) {
        this.index = index;
    }

    /** A stable hash of the node's type plus its sorted typed one-step neighborhood. */
    public String signature(NodeId id) {
        GraphNode g = index.node(id).orElseThrow(() -> new IllegalArgumentException("unknown node: " + id));
        List<String> triples = new ArrayList<>();
        for (GraphEdge e : index.out(id)) triples.add("OUT|" + e.family() + "|" + labelOf(e.to()));
        for (GraphEdge e : index.in(id)) triples.add("IN|" + e.family() + "|" + labelOf(e.from()));
        Collections.sort(triples);
        String basis = g.label() + "::" + String.join(",", triples);
        return sha256(basis);
    }

    /** Nodes (other than {@code id}) with the same label and the same neighborhood signature, in total order. */
    public List<GraphNode> similarTo(NodeId id) {
        GraphNode subject = index.node(id).orElseThrow(() -> new IllegalArgumentException("unknown node: " + id));
        String target = signature(id);
        List<GraphNode> matches = new ArrayList<>();
        for (GraphNode g : index.nodes()) {
            if (g.id().equals(id)) continue;
            if (!g.label().equals(subject.label())) continue;
            if (signature(g.id()).equals(target)) matches.add(g);
        }
        matches.sort(Order.NODES);
        return List.copyOf(matches);
    }

    /** Neighborhood signatures shared by at least {@code minCount} nodes — recurring engineering shapes. */
    public List<Pattern> patterns(int minCount) {
        Map<String, List<NodeId>> bySig = new LinkedHashMap<>();
        for (GraphNode g : index.nodes()) {
            bySig.computeIfAbsent(signature(g.id()), k -> new ArrayList<>()).add(g.id());
        }
        List<Pattern> out = new ArrayList<>();
        for (var entry : bySig.entrySet()) {
            if (entry.getValue().size() >= minCount) out.add(new Pattern(entry.getKey(), entry.getValue()));
        }
        out.sort((a, b) -> {
            int c = Integer.compare(b.count(), a.count()); // most frequent first
            return c != 0 ? c : a.signature().compareTo(b.signature());
        });
        return List.copyOf(out);
    }

    private String labelOf(NodeId id) {
        return index.node(id).map(GraphNode::label).orElse("?");
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
