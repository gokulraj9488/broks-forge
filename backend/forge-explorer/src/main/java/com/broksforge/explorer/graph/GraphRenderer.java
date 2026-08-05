package com.broksforge.explorer.graph;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.explorer.render.Payloads;
import com.broksforge.explorer.render.Values;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link GraphModel} for human eyes: a compact ASCII report, and a Graphviz DOT document
 * that can be pasted into any DOT viewer to see the engineering graph laid out.
 */
public final class GraphRenderer {

    private GraphRenderer() {
    }

    // ---- ASCII -------------------------------------------------------------------------------

    /**
     * @param model a folded graph model
     * @return a multi-line ASCII report of nodes, names, and edges
     */
    public static String ascii(GraphModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("ENGINEERING GRAPH\n");
        sb.append("  nodes: ").append(model.nodes().size());
        for (Kind k : Kind.values()) {
            long n = model.nodesOfKind(k).size();
            if (n > 0) {
                sb.append("   ").append(k.wireName()).append('=').append(n);
            }
        }
        sb.append("   edges: ").append(model.allEdges().size())
                .append(" (").append(model.intrinsicEdges().size()).append(" intrinsic, ")
                .append(model.extrinsicEdges().size()).append(" extrinsic)")
                .append("   names: ").append(model.names().size());
        if (model.tickCount() > 0) {
            sb.append("   ticks: ").append(model.tickCount());
        }
        sb.append("\n\n");

        sb.append("  NODES\n");
        for (GraphModel.Node node : model.nodes()) {
            sb.append(String.format("    %-11s %-22s %s  (%d rev)%n",
                    node.kind().wireName(),
                    node.latest().subtype(),
                    Payloads.shortHash(node.latest().hash()),
                    node.revisions().size()));
            sb.append("        ").append(Values.oneLine(node.latest().payload())).append('\n');
        }

        if (!model.names().isEmpty()) {
            sb.append("\n  NAMES\n");
            model.names().forEach((path, target) ->
                    sb.append(String.format("    %-24s → %s%n", path, Payloads.shortHash(target.revision()))));
        }

        sb.append("\n  EDGES  (continuant level, de-duplicated across revisions)\n");
        List<GraphModel.Relationship> edges = distinctEdges(model);
        for (EdgeFamily family : EdgeFamily.values()) {
            edges.stream().filter(e -> e.verb().family() == family).forEach(e ->
                    sb.append(String.format("    [%-11s] %-13s %s → %s%n",
                            family.wireName(), e.verb().name(),
                            label(model, e.from()), label(model, e.to()))));
        }
        return sb.toString();
    }

    // ---- DOT ---------------------------------------------------------------------------------

    /**
     * @param model a folded graph model
     * @return a Graphviz DOT document rendering the graph at the continuant level
     */
    public static String dot(GraphModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph forge_graph {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  node [style=filled, fontname=\"monospace\", shape=box];\n");
        sb.append("  edge [fontname=\"monospace\", fontsize=10];\n\n");

        for (GraphModel.Node node : model.nodes()) {
            sb.append("  ").append(dotId(node.id().toString()))
                    .append(" [label=\"").append(node.kind().wireName()).append("/")
                    .append(node.latest().subtype()).append("\\n")
                    .append(shortId(node.id().toString())).append("\", fillcolor=\"")
                    .append(color(node.kind())).append("\"];\n");
        }

        sb.append('\n');
        Map<String, Boolean> seenName = new LinkedHashMap<>();
        for (GraphModel.Relationship e : distinctEdges(model)) {
            String from = endpointId(e.from(), seenName, sb);
            String to = endpointId(e.to(), seenName, sb);
            sb.append("  ").append(from).append(" -> ").append(to)
                    .append(" [label=\"").append(e.verb().name()).append("\", color=\"")
                    .append(edgeColor(e.verb().family())).append("\"")
                    .append(e.intrinsic() ? "" : ", style=dashed").append("];\n");
        }

        model.names().forEach((path, target) -> {
            String nameId = dotId("name_" + path);
            sb.append("  ").append(nameId).append(" [label=\"").append(path)
                    .append("\", shape=note, fillcolor=\"white\"];\n");
            model.addressOf(target.revision()).ifPresent(addr ->
                    sb.append("  ").append(nameId).append(" -> ").append(dotId(addr.node().toString()))
                            .append(" [style=dotted, label=\"resolves\"];\n"));
        });

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Collapses the revision-level relationships to distinct continuant-level display edges: two
     * revisions of the same node that both declare {@code uses prompt} appear once, not once per
     * revision. Order is preserved (intrinsic first, then extrinsic).
     */
    private static java.util.List<GraphModel.Relationship> distinctEdges(GraphModel model) {
        java.util.Map<String, GraphModel.Relationship> distinct = new java.util.LinkedHashMap<>();
        for (GraphModel.Relationship e : model.allEdges()) {
            String key = endpointKey(e.from()) + '|' + e.verb().name() + '|'
                    + endpointKey(e.to()) + '|' + e.intrinsic();
            distinct.putIfAbsent(key, e);
        }
        return new java.util.ArrayList<>(distinct.values());
    }

    private static String endpointKey(Address address) {
        return switch (address) {
            case Address.Revision r -> "n:" + r.node();
            case Address.Node n -> "n:" + n.node();
            case Address.NamePointer np -> "name:" + np.name().path();
        };
    }

    private static String endpointId(Address address, Map<String, Boolean> seenName, StringBuilder sb) {
        return switch (address) {
            case Address.Revision r -> dotId(r.node().toString());
            case Address.Node n -> dotId(n.node().toString());
            case Address.NamePointer np -> {
                String id = dotId("name_" + np.name().path());
                if (seenName.putIfAbsent(id, true) == null) {
                    sb.append("  ").append(id).append(" [label=\"").append(np.name().path())
                            .append("\", shape=note, fillcolor=\"white\"];\n");
                }
                yield id;
            }
        };
    }

    private static String label(GraphModel model, Address address) {
        return switch (address) {
            case Address.Revision r -> model.addressOf(r.revision())
                    .map(a -> nodeLabel(model, a.revision()))
                    .orElse(Payloads.shortHash(r.revision()));
            case Address.Node n -> shortId(n.node().toString());
            case Address.NamePointer np -> "name:" + np.name().path();
        };
    }

    private static String nodeLabel(GraphModel model, RevisionHash hash) {
        return model.nodes().stream()
                .filter(n -> n.revisions().stream().anyMatch(rev -> rev.hash().equals(hash)))
                .findFirst()
                .map(n -> n.kind().wireName() + "/" + n.latest().subtype())
                .orElse(Payloads.shortHash(hash));
    }

    private static String color(Kind kind) {
        return switch (kind) {
            case ARTIFACT -> "lightskyblue";
            case OBSERVATION -> "palegreen";
            case CLAIM -> "khaki";
            case DECISION -> "plum";
        };
    }

    private static String edgeColor(EdgeFamily family) {
        return switch (family) {
            case COMPOSITION -> "steelblue";
            case DERIVATION -> "darkgreen";
            case EVIDENCE -> "goldenrod";
            case CAUSALITY -> "firebrick";
            case INTENT -> "purple";
        };
    }

    private static String dotId(String raw) {
        StringBuilder sb = new StringBuilder("g_");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    private static String shortId(String uuid) {
        return uuid.length() >= 8 ? uuid.substring(0, 8) : uuid;
    }
}
