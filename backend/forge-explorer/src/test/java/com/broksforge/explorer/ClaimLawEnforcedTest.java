package com.broksforge.explorer;

import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.KernelException;
import com.broksforge.explorer.kinds.Claims;
import com.broksforge.explorer.kinds.Decisions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for <b>KAP-1 (ACCEPTED)</b>: the Claim Law and Decision Law are now enforced by the
 * kernel at append time. This was the headline Phase 1.5 dogfooding finding — the kernel used to accept
 * a naked-number claim; after the Kernel Amendment Review it does not.
 *
 * <p>The application's userspace {@link Claims}/{@link Decisions} helpers still fail fast before the
 * append (belt and suspenders), and their output is exactly what the kernel now accepts.
 */
class ClaimLawEnforcedTest {

    @Test
    @DisplayName("KAP-1: the kernel now REJECTS a naked-number CLAIM (was accepted pre-amendment)")
    void kernelRejectsNakedClaim() {
        ForgeKernel kernel = TestKernel.kernel();
        Revision nakedClaim = Revision.leaf(Kind.CLAIM, "kpi", CanonicalValue.of(new BigDecimal("42")));
        KernelException ex = assertThrows(KernelException.class, () ->
                kernel.append(TestKernel.ORG, new AppendCommand.CreateNode(nakedClaim), TestKernel.ACTOR));
        assertEquals(KernelException.Reason.CLAIM_LAW, ex.reason());
    }

    @Test
    @DisplayName("KAP-1: the kernel now REJECTS a decision that cites nothing and declares no judgment call")
    void kernelRejectsUnjustifiedDecision() {
        ForgeKernel kernel = TestKernel.kernel();
        Revision unjustified = Revision.leaf(Kind.DECISION, "promotion",
                CanonicalValue.objectBuilder().put("statement", "ship it").build());
        KernelException ex = assertThrows(KernelException.class, () ->
                kernel.append(TestKernel.ORG, new AppendCommand.CreateNode(unjustified), TestKernel.ACTOR));
        assertEquals(KernelException.Reason.DECISION_LAW, ex.reason());
    }

    @Test
    @DisplayName("KAP-1: a lawful claim and a decision resting on it are accepted end-to-end")
    void lawfulClaimAndDecisionAccepted() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle observation = forge.recordObservation("metric", CanonicalValue.objectBuilder()
                .put("name", "p95_ms").put("value", CanonicalValue.of(812)).build());
        Handle claim = forge.create(Claims.claim("regression-verdict", "p95 regressed", "welch-t-test:v2",
                new BigDecimal("0.87"), List.of(observation.hash())));
        Handle decision = forge.create(Decisions.restingOn("rollback", "roll back to v1",
                List.of(claim.hash())));
        assertEquals(Kind.CLAIM, claim.kind());
        assertEquals(Kind.DECISION, decision.kind());
        assertTrue(forge.verifyChain());
    }

    @Test
    @DisplayName("KAP-1: a judgment-call decision (no cited claims) is accepted")
    void judgmentCallAccepted() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle decision = forge.create(Decisions.judgmentCall("accept-risk",
                "ship despite the unknown", "time-boxed spike; risk accepted"));
        assertEquals(Kind.DECISION, decision.kind());
    }

    @Test
    @DisplayName("the userspace helpers still fail fast before the append (defense in depth)")
    void userspaceHelpersFailFast() {
        assertThrows(IllegalArgumentException.class, () ->
                Claims.claim("kpi", "latency is fine", "eyeball:v0", new BigDecimal("0.9"), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                Decisions.restingOn("promotion", "ship it", List.of()));
    }
}
