package com.broksforge.explorer.demo;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.Kernels;
import com.broksforge.kernel.core.event.Subscription;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.op.Delta;
import com.broksforge.kernel.core.op.Query;
import com.broksforge.kernel.core.op.Subgraph;
import com.broksforge.kernel.core.reproduce.ReproduceResult;
import com.broksforge.kernel.core.validate.IntegrityReport;
import com.broksforge.kernel.core.validate.IntegrityScanner;
import com.broksforge.explorer.ForgeExplorer;
import com.broksforge.explorer.Handle;
import com.broksforge.explorer.Verbs;
import com.broksforge.explorer.graph.GraphModel;
import com.broksforge.explorer.graph.GraphRenderer;
import com.broksforge.explorer.kinds.Claims;
import com.broksforge.explorer.kinds.Decisions;
import com.broksforge.explorer.render.Payloads;
import com.broksforge.explorer.reproduce.ChecklistReproducer;
import com.broksforge.explorer.watch.AuditTrailProgram;
import com.broksforge.explorer.watch.AutoObserverProgram;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Forge Engineering Explorer end-to-end demonstration.
 *
 * <p>One narrated scenario that puts a real AI-engineering workflow through every major kernel
 * capability, using nothing but the public API (via the {@link ForgeExplorer} facade): build and
 * version artifacts, observe reality, form claims from evidence, decide, deploy/promote/rollback names,
 * assert every edge family, reproduce a check-suite through the SPI, react through subscriptions, then
 * resolve, time-travel, traverse, take a closure snapshot, diff, verify the chain, scan integrity, and
 * visualize the graph.
 */
public final class EngineeringExplorerDemo {

    private EngineeringExplorerDemo() {
    }

    /**
     * @param args ignored
     */
    public static void main(String[] args) {
        // Emit UTF-8 explicitly so the box-drawing and arrow glyphs render faithfully when the output
        // is redirected to a file or pipe, independent of the platform console code page.
        PrintStream utf8 = new PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8);
        run(utf8);
    }

    /**
     * Runs the full demonstration.
     *
     * @param out where to narrate
     * @return the org that was populated (so callers/tests can inspect it)
     */
    public static OrgId run(PrintStream out) {
        // A deterministic node-id minter keeps the narration stable run to run; a real deployment uses
        // the random default (Kernels.inMemory()).
        ForgeKernel kernel = Kernels.inMemory(new ChecklistReproducer());
        OrgId org = OrgId.of(UUID.fromString("0000feed-0000-4000-8000-00000000d0d0"));
        ActorId alice = ActorId.of("engineer:alice");
        ForgeExplorer forge = ForgeExplorer.open(kernel, org, alice);

        banner(out, "FORGE ENGINEERING EXPLORER — Phase 1.5 kernel dogfooding");
        out.println("Built on the public Forge Kernel API only (kernel-core / kernel-api 1.0.0).");
        out.println("Org: " + org);

        // ---- subscriptions: reaction is subscription (op 6) -----------------------------------
        AuditTrailProgram audit = new AuditTrailProgram();
        Subscription auditSub = forge.subscribe(e -> true, audit);
        AutoObserverProgram autoObserver = new AutoObserverProgram();
        Subscription autoSub = forge.subscribe(AutoObserverProgram.onNewArtifact(), autoObserver);

        // ---- 1. artifacts + composition (intrinsic refs) --------------------------------------
        section(out, "1  ARTIFACTS & COMPOSITION   (append: CreateNode; family: composition)");
        Handle promptV1 = forge.createArtifact("prompt",
                obj("text", "Answer the ticket: {{ticket}}"));
        Handle retrieval = forge.createArtifact("retrieval-config",
                obj("index", "support-kb", "top_k", CanonicalValue.of(5)));
        Handle agentV1 = forge.createArtifact("agent",
                obj("name", "support-agent", "model", "claude-sonnet-5"),
                Ref.of(Verbs.USES, promptV1.hash()),
                Ref.of(Verbs.DEPENDS_ON, retrieval.hash()));
        out.println("  prompt    v1  " + Payloads.shortHash(promptV1.hash()));
        out.println("  retrieval     " + Payloads.shortHash(retrieval.hash()));
        out.println("  agent     v1  " + Payloads.shortHash(agentV1.hash())
                + "   uses=prompt.v1, depends_on=retrieval");

        // ---- 2. versioning + derivation -------------------------------------------------------
        section(out, "2  VERSIONING & DERIVATION   (append: AddRevision; family: derivation)");
        Handle promptV2 = forge.addRevision(promptV1.node(),
                com.broksforge.kernel.api.Revision.of(promptV1.kind(), "prompt",
                        obj("text", "Answer the ticket concisely and cite sources: {{ticket}}"),
                        List.of(Ref.of(Verbs.DERIVED_FROM, promptV1.hash()))));
        Handle agentV2 = forge.addRevision(agentV1.node(),
                com.broksforge.kernel.api.Revision.of(agentV1.kind(), "agent",
                        obj("name", "support-agent", "model", "claude-sonnet-5"),
                        List.of(Ref.of(Verbs.USES, promptV2.hash()),
                                Ref.of(Verbs.DEPENDS_ON, retrieval.hash()),
                                Ref.of(Verbs.DERIVED_FROM, agentV1.hash()))));
        out.println("  prompt    v2  " + Payloads.shortHash(promptV2.hash()) + "   derived_from prompt.v1");
        out.println("  agent     v2  " + Payloads.shortHash(agentV2.hash()) + "   uses=prompt.v2, derived_from agent.v1");

        // ---- 3. names: deploy / promote (CAS) -------------------------------------------------
        section(out, "3  DEPLOY & PROMOTE   (append: RepointName, compare-and-swap)");
        Name current = Name.of("agents/support/current");
        forge.deploy(current, agentV1);
        out.println("  deploy  " + current + "  → agent.v1");
        forge.repoint(current, agentV2.address(), agentV1.address());
        out.println("  promote " + current + "  → agent.v2   (CAS expected v1)");
        long posAtV2 = lastPosition(forge);

        // ---- 4. observations (reality) --------------------------------------------------------
        section(out, "4  OBSERVATIONS   (kind: observation)");
        Handle latency = forge.recordObservation("metric",
                obj("name", "p95_latency_ms", "value", CanonicalValue.of(new BigDecimal("812"))));
        Handle errors = forge.recordObservation("metric",
                obj("name", "error_rate", "value", CanonicalValue.of(new BigDecimal("0.041"))));
        out.println("  observed  p95_latency_ms = 812      " + Payloads.shortHash(latency.hash()));
        out.println("  observed  error_rate     = 0.041    " + Payloads.shortHash(errors.hash()));

        // ---- 5. claims (Claim Law honored in userspace) --------------------------------------
        section(out, "5  CLAIMS   (kind: claim; family: evidence; the Claim Law — no naked numbers)");
        Handle regression = forge.create(Claims.claim("regression-verdict",
                "p95 latency regressed after the prompt-v2 rollout",
                "welch-t-test:v2",
                new BigDecimal("0.87"),
                List.of(latency.hash(), errors.hash())));
        out.println("  claim  " + Payloads.shortHash(regression.hash())
                + "   method=welch-t-test:v2 confidence=0.87 cites=[latency, error_rate]");
        out.println("  (the kernel does NOT enforce this law — see the Kernel Amendment Proposal)");

        // ---- 6. causality edge ----------------------------------------------------------------
        section(out, "6  CAUSALITY   (append: AssertEdge; family: causality)");
        forge.assertEdge(agentV2.address(), Verbs.CAUSED, latency.address());
        out.println("  edge  agent.v2  --caused-->  p95_latency observation");

        // ---- 7. decision (Decision Law honored) + intent edge + rollback ---------------------
        section(out, "7  DECISION, INTENT & ROLLBACK   (kind: decision; family: intent)");
        Handle rollback = forge.create(Decisions.restingOn("rollback",
                "Roll support agent back to v1 pending a latency fix",
                List.of(regression.hash())));
        forge.assertEdge(rollback.address(), Verbs.APPLIED, agentV1.address());
        forge.repoint(current, agentV1.address(), agentV2.address());
        out.println("  decision  " + Payloads.shortHash(rollback.hash()) + "   rests_on the regression claim");
        out.println("  edge      decision --applied--> agent.v1");
        out.println("  rollback  " + current + "  → agent.v1   (CAS expected v2)");

        // ---- 8. reproduce a check-suite through the SPI ---------------------------------------
        section(out, "8  REPRODUCE   (op 5: reproducer SPI; family: derivation via generated_from)");
        Handle suite = forge.createArtifact("check-suite",
                CanonicalValue.objectBuilder()
                        .put("name", "support-agent-smoke")
                        .put("checks", CanonicalValue.array(
                                CanonicalValue.of("answers-in-english"),
                                CanonicalValue.of("cites-a-source"),
                                CanonicalValue.of("latency-under-1s")))
                        .build());
        ReproduceResult first = forge.reproduce(suite.address());
        ReproduceResult second = forge.reproduce(suite.address());
        out.println("  " + first.detail());
        out.println("  run 1 produced " + first.observations().size() + " observations");
        out.println("  run 2 produced " + second.observations().size()
                + " observations — identical content hashes (reproducible), distinct facts:");
        out.println("    run1[0] = " + hashOf(first.observations().get(0)));
        out.println("    run2[0] = " + hashOf(second.observations().get(0))
                + "   equal=" + hashOf(first.observations().get(0)).equals(hashOf(second.observations().get(0))));

        // ---- 9. tick + edge retraction --------------------------------------------------------
        section(out, "9  CLOCK TICK & EDGE RETRACTION   (append: Tick, RetractEdge)");
        forge.tick(Instant.parse("2026-07-27T00:00:00Z"));
        forge.retractEdge(agentV2.address(), Verbs.CAUSED, latency.address());
        out.println("  tick emitted; causality edge retracted (it and its retraction both remain in history)");

        // ---- close the autonomous observer; the audit tail keeps running ----------------------
        autoSub.close();
        out.println("\n  autonomous observer appended " + autoObserver.appendedCount()
                + " observations (Law 9: no privileged writer)");

        // ---- 10. RESOLVE + time travel --------------------------------------------------------
        section(out, "10 RESOLVE & TIME TRAVEL   (op 2: resolve, resolveAt)");
        out.println("  resolve now              " + current + "  → " + shortResolved(forge.resolve(current)));
        out.println("  resolveAt pos " + posAtV2 + " (post-promote)  → "
                + shortResolved(forge.resolveAt(current, new LogPosition(posAtV2))));

        // ---- 11. TRAVERSE ---------------------------------------------------------------------
        section(out, "11 TRAVERSE   (op 3: graph BFS, OUT / IN)");
        Subgraph out2 = forge.traverse(new Query(agentV2.address(),
                java.util.EnumSet.of(EdgeFamily.COMPOSITION, EdgeFamily.DERIVATION),
                Query.Direction.OUT, 3));
        out.println("  OUT from agent.v2 (composition+derivation): "
                + out2.nodes().size() + " nodes, " + out2.edges().size() + " edges");
        Subgraph in = forge.traverse(new Query(promptV2.address(),
                java.util.EnumSet.noneOf(EdgeFamily.class), Query.Direction.IN, 3));
        out.println("  IN  to  prompt.v2 (who uses/derives it): "
                + in.nodes().size() + " nodes, " + in.edges().size() + " edges");

        // ---- 12. CLOSURE (system snapshot / reproducibility certificate) ----------------------
        section(out, "12 CLOSURE   (distinguished traversal: composition closure = system snapshot)");
        Map<RevisionHash, com.broksforge.kernel.api.Revision> snapshot = forge.closure(agentV2.hash());
        out.println("  closure(agent.v2) = " + snapshot.size() + " revisions (root first)");
        out.println("  reproducibility certificate (root hash): " + agentV2.hash());
        snapshot.keySet().forEach(h -> out.println("    - " + Payloads.shortHash(h)));

        // ---- 13. DIFF -------------------------------------------------------------------------
        section(out, "13 DIFF   (op 4: structural delta over canonical content)");
        Delta delta = forge.diff(promptV1.hash(), promptV2.hash());
        out.println("  diff(prompt.v1, prompt.v2): " + delta.changes().size() + " change(s)");
        for (Delta.Change c : delta.changes()) {
            out.println("    " + c.kind() + "  " + c.path()
                    + "   " + trunc(c.left()) + "  →  " + trunc(c.right()));
        }

        // ---- 14. AUDIT: chain + integrity scan (validation layer) -----------------------------
        section(out, "14 AUDIT   (verifyChain + IntegrityScanner validation layer)");
        out.println("  verifyChain: " + forge.verifyChain());
        IntegrityReport report = new IntegrityScanner().scan(kernel, org);
        out.println("  integrity scan: " + (report.clean() ? "CLEAN" : report.errorCount() + " error(s)")
                + " over " + forge.log().size() + " log entries");
        out.println("  audit subscription observed " + audit.count() + " committed entries");

        // ---- 15. LOG tail ---------------------------------------------------------------------
        section(out, "15 LOG   (append log = source of truth = event stream)");
        List<LogEntry> log = forge.log();
        int from = Math.max(0, log.size() - 8);
        out.println("  last " + (log.size() - from) + " of " + log.size() + " entries:");
        for (int i = from; i < log.size(); i++) {
            out.println("    " + Payloads.describe(log.get(i)));
        }

        // ---- 16. VISUALIZE --------------------------------------------------------------------
        section(out, "16 VISUALIZE   (graph folded from the log)");
        GraphModel model = GraphModel.of(kernel, org);
        out.println(indent(GraphRenderer.ascii(model)));
        out.println("  --- Graphviz DOT (paste into any DOT viewer) ---");
        out.println(indent(GraphRenderer.dot(model)));

        auditSub.close();
        banner(out, "END — every major kernel capability exercised via public API only");
        return org;
    }

    // ---- small narration helpers -------------------------------------------------------------

    private static CanonicalValue obj(String k1, String v1) {
        return CanonicalValue.objectBuilder().put(k1, v1).build();
    }

    private static CanonicalValue obj(String k1, String v1, String k2, CanonicalValue v2) {
        return CanonicalValue.objectBuilder().put(k1, v1).put(k2, v2).build();
    }

    private static CanonicalValue obj(String k1, String v1, String k2, String v2) {
        return CanonicalValue.objectBuilder().put(k1, v1).put(k2, v2).build();
    }

    private static long lastPosition(ForgeExplorer forge) {
        List<LogEntry> log = forge.log();
        return log.get(log.size() - 1).position().value();
    }

    private static String hashOf(Address address) {
        return address instanceof Address.Revision r ? Payloads.shortHash(r.revision()) : address.toUri();
    }

    private static String shortResolved(java.util.Optional<Address.Revision> resolved) {
        return resolved.map(r -> Payloads.shortHash(r.revision())).orElse("(unresolved)");
    }

    private static String trunc(String s) {
        if (s == null) {
            return "∅";
        }
        return s.length() <= 40 ? s : s.substring(0, 39) + "…";
    }

    private static String indent(String block) {
        return block.lines().map(l -> "  " + l).reduce("", (a, b) -> a + b + "\n");
    }

    private static void banner(PrintStream out, String title) {
        out.println();
        out.println("=".repeat(78));
        out.println("  " + title);
        out.println("=".repeat(78));
    }

    private static void section(PrintStream out, String title) {
        out.println();
        out.println("── " + title + " " + "─".repeat(Math.max(0, 74 - title.length())));
    }
}
