package com.broksforge.explorer;

import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.core.validate.IntegrityReport;
import com.broksforge.kernel.core.validate.IntegrityScanner;
import com.broksforge.explorer.demo.EngineeringExplorerDemo;
import com.broksforge.explorer.graph.GraphModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the full narrated demonstration and asserts the end state is healthy: the demo self-reports
 * success, the run completes without throwing, and the demo output records a verified chain and a clean
 * integrity scan. Printing the captured narration lets {@code mvn test} surface the full walkthrough.
 */
class EndToEndWorkflowTest {

    @Test
    @DisplayName("the end-to-end demo runs cleanly and reports a verified, integral graph")
    void demoRunsCleanly() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            EngineeringExplorerDemo.run(out);
        }
        String narration = buffer.toString(StandardCharsets.UTF_8);

        // Surface the walkthrough in the build log.
        System.out.println(narration);

        assertTrue(narration.contains("verifyChain: true"), "chain should verify");
        assertTrue(narration.contains("integrity scan: CLEAN"), "integrity should be clean");
        assertTrue(narration.contains("equal=true"),
                "reproducing the same suite twice should yield identical content hashes");
        assertFalse(narration.contains("(unresolved)"), "names should resolve");
    }

    @Test
    @DisplayName("the folded graph model surfaces every kind, edge family, and name")
    void graphModelCoversEveryKind() {
        ForgeExplorer forge = TestKernel.explorer(new com.broksforge.explorer.reproduce.ChecklistReproducer());

        Handle prompt = forge.createArtifact("prompt",
                com.broksforge.kernel.api.canonical.CanonicalValue.objectBuilder().put("text", "p").build());
        // Version the prompt to introduce a derivation edge (derived_from).
        forge.addRevision(prompt.node(), com.broksforge.kernel.api.Revision.of(
                Kind.ARTIFACT, "prompt",
                com.broksforge.kernel.api.canonical.CanonicalValue.objectBuilder().put("text", "p2").build(),
                java.util.List.of(com.broksforge.kernel.api.Ref.of(Verbs.DERIVED_FROM, prompt.hash()))));
        Handle agent = forge.createArtifact("agent",
                com.broksforge.kernel.api.canonical.CanonicalValue.objectBuilder().put("name", "a").build(),
                com.broksforge.kernel.api.Ref.of(Verbs.USES, prompt.hash()));
        Handle metric = forge.recordObservation("metric",
                com.broksforge.kernel.api.canonical.CanonicalValue.objectBuilder()
                        .put("name", "n").put("value", com.broksforge.kernel.api.canonical.CanonicalValue.of(1)).build());
        Handle claim = forge.create(com.broksforge.explorer.kinds.Claims.claim("verdict", "s", "m:v1",
                new java.math.BigDecimal("0.9"), java.util.List.of(metric.hash())));
        Handle decision = forge.create(com.broksforge.explorer.kinds.Decisions.restingOn(
                "promotion", "ship", java.util.List.of(claim.hash())));
        forge.assertEdge(agent.address(), Verbs.CAUSED, metric.address());
        forge.assertEdge(decision.address(), Verbs.APPLIED, agent.address());
        forge.deploy(com.broksforge.kernel.api.Name.of("prod"), agent);

        GraphModel model = GraphModel.of(forge.kernel(), forge.org());
        for (Kind kind : Kind.values()) {
            assertFalse(model.nodesOfKind(kind).isEmpty(), () -> "missing kind " + kind);
        }
        java.util.Set<com.broksforge.kernel.api.EdgeFamily> families = new java.util.HashSet<>();
        model.allEdges().forEach(e -> families.add(e.verb().family()));
        assertEquals(com.broksforge.kernel.api.EdgeFamily.values().length, families.size(),
                "every edge family should appear: " + families);
        assertTrue(model.names().containsKey("prod"));

        IntegrityReport report = new IntegrityScanner().scan(forge.kernel(), forge.org());
        assertTrue(report.clean());
        assertEquals(0, report.errorCount());
    }
}
