package com.broksforge.fxp.cli;

import com.broksforge.fkge.KnowledgeGraphEngine;
import com.broksforge.fkge.depend.DependencySet;
import com.broksforge.fkge.explain.Explanation;
import com.broksforge.fkge.explain.ExplanationStep;
import com.broksforge.fkge.impact.Impact;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.provenance.Provenance;
import com.broksforge.fkge.reason.CausalTrace;
import com.broksforge.fkge.reason.ConfidenceResult;
import com.broksforge.fxp.ForgeClient;
import com.broksforge.fxp.PlatformHealth;
import com.broksforge.fvcs.history.CommitNode;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.knowledge.graph.KnowledgeObject;

import java.util.List;
import java.util.Optional;

/**
 * Forge CLI — the conceptual API for terminals and CI. Deterministic and greppable: every command returns
 * text (so it is testable without a console), and every knowledge answer prints the {@code asOf} log
 * position it was computed at, so a result can be reproduced. The CLI holds no logic; it formats
 * {@link ForgeClient} results.
 */
public final class ForgeCli {

    private final ForgeClient client;
    private final KnowledgeGraphEngine fkge;

    public ForgeCli(ForgeClient client) {
        this.client = client;
        this.fkge = client.engine();
    }

    /** Dispatch a command. Returns the rendered output; never throws for a bad request — it returns usage. */
    public String run(String... args) {
        if (args.length == 0) return usage();
        String cmd = args[0];
        try {
            return switch (cmd) {
                case "explain" -> explain(node(args));
                case "provenance" -> provenance(node(args));
                case "impact" -> impact(node(args));
                case "dependencies", "deps" -> dependencies(node(args));
                case "root-cause" -> rootCause(node(args));
                case "confidence" -> confidence(node(args));
                case "evidence" -> evidence(node(args));
                case "history" -> history(arg(args));
                case "search" -> search(arg(args));
                case "reproduce" -> reproduce(node(args));
                case "validate" -> validate();
                case "help", "--help", "-h" -> usage();
                default -> "unknown command: " + cmd + "\n" + usage();
            };
        } catch (IllegalArgumentException e) {
            return "error: " + e.getMessage();
        }
    }

    private String explain(NodeId n) {
        Explanation e = fkge.explain(n);
        StringBuilder sb = new StringBuilder("explain " + label(n) + "  (asOf " + e.asOf().value() + ")");
        for (ExplanationStep s : e.steps()) {
            sb.append("\n  ").append(label(s.from())).append(" --").append(s.verb().name())
                    .append("--> ").append(label(s.to()));
        }
        sb.append("\n  complete=").append(e.complete());
        if (!e.gaps().isEmpty()) e.gaps().forEach(g -> sb.append("\n  gap: ").append(g));
        return sb.toString();
    }

    private String provenance(NodeId n) {
        Provenance p = fkge.provenanceOf(n);
        StringBuilder sb = new StringBuilder("provenance " + label(n) + "  (asOf " + p.asOf().value() + ")");
        sb.append("\n  certificate: ").append(p.certificate());
        for (GraphNode a : p.ancestors()) sb.append("\n  - ").append(a.label());
        return sb.toString();
    }

    private String impact(NodeId n) {
        Impact im = fkge.impactOf(n);
        StringBuilder sb = new StringBuilder("impact " + label(n) + "  radius=" + im.radius()
                + "  (asOf " + im.asOf().value() + ")");
        for (GraphNode d : im.dependents()) sb.append("\n  - ").append(d.label());
        return sb.toString();
    }

    private String dependencies(NodeId n) {
        DependencySet ds = fkge.dependenciesOf(n);
        StringBuilder sb = new StringBuilder("dependencies " + label(n) + "  count=" + ds.size()
                + "  (asOf " + ds.asOf().value() + ")");
        for (GraphNode d : ds.nodes()) sb.append("\n  - ").append(d.label());
        return sb.toString();
    }

    private String rootCause(NodeId n) {
        CausalTrace t = fkge.rootCause(n);
        StringBuilder sb = new StringBuilder("root-cause " + label(n) + "  sound=" + t.sound()
                + "  (asOf " + t.asOf().value() + ")");
        for (GraphNode c : t.causes()) sb.append("\n  - ").append(c.label());
        t.anomalies().forEach(a -> sb.append("\n  anomaly: ").append(a));
        return sb.toString();
    }

    private String confidence(NodeId n) {
        ConfidenceResult c = fkge.confidenceOf(n);
        if (!c.defined()) return "confidence " + label(n) + ": undefined (not a truth-bearer)";
        return "confidence " + label(n) + ": " + c.confidence()
                + "  weakest=" + label(c.weakestLink()) + "  (asOf " + c.asOf().value() + ")";
    }

    private String evidence(NodeId n) {
        List<GraphNode> ev = fkge.evidenceFor(n);
        StringBuilder sb = new StringBuilder("evidence " + label(n) + "  count=" + ev.size());
        for (GraphNode e : ev) sb.append("\n  - ").append(e.label());
        return sb.toString();
    }

    private String history(String branchLine) {
        List<CommitNode> hist = client.studio().history(client.repository().branch(branchLine));
        StringBuilder sb = new StringBuilder("history " + branchLine + "  commits=" + hist.size());
        for (CommitNode c : hist) sb.append("\n  ").append(c.hash()).append("  ").append(c.message());
        return sb.toString();
    }

    private String search(String text) {
        List<KnowledgeObject> hits = client.search(text);
        StringBuilder sb = new StringBuilder("search \"" + text + "\"  hits=" + hits.size());
        for (KnowledgeObject o : hits) sb.append("\n  ").append(o.type().name()).append("  ").append(o.node());
        return sb.toString();
    }

    private String reproduce(NodeId n) {
        Optional<KnowledgeObject> obj = client.repository().knowledge().view().object(n);
        if (obj.isEmpty()) return "reproduce: unknown node " + n;
        var result = client.reproduce(obj.get());
        return "reproduce " + label(n) + ": reproduced=" + result.reproduced() + "  " + result.detail();
    }

    private String validate() {
        PlatformHealth h = client.validate();
        return "validate: chainValid=" + h.chainValid()
                + "  integrityClean=" + h.integrity().clean()
                + "  errors=" + h.integrity().errorCount()
                + "  healthy=" + h.healthy();
    }

    private String label(NodeId id) {
        return fkge.index().node(id).map(GraphNode::label).orElse(String.valueOf(id));
    }

    private static NodeId node(String[] args) {
        return NodeId.fromString(arg(args));
    }

    private static String arg(String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("missing argument for '" + args[0] + "'");
        return args[1];
    }

    public static String usage() {
        return String.join("\n",
                "forge <command> <arg>",
                "  explain <nodeId>        proof tree of why a node exists / was decided",
                "  provenance <nodeId>     certified derivation history",
                "  impact <nodeId>         blast radius",
                "  dependencies <nodeId>   reproduction-bearing dependencies",
                "  root-cause <nodeId>     causal trace of an incident",
                "  confidence <nodeId>     min-bound confidence",
                "  evidence <nodeId>       supporting observations/artifacts",
                "  history <branch>        commit history of a branch",
                "  search <text>           object search",
                "  reproduce <nodeId>      reproduce an artifact through the kernel",
                "  validate                verify the hash chain + integrity scan");
    }
}
