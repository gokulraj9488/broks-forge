package com.broksforge.kernel.core;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.KernelException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Law 5 — the Claim Law (MANIFESTO §V.5, ADR-V2-0003, DOMAIN_MODEL §3.3), enforced at append time by
 * {@code KindLaws}. A claim is unappendable without a statement, a named method, a calibrated
 * confidence in [0,1], and at least one evidence reference; "no unexplained number can exist anywhere."
 */
class ClaimLawTest {

    private static final Verb CITES = new Verb("cites", EdgeFamily.EVIDENCE);

    private static RevisionHash anObservation(ForgeKernel k) {
        Address.Revision obs = (Address.Revision) k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(Revision.leaf(Kind.OBSERVATION, "metric",
                        CanonicalValue.objectBuilder().put("name", "p95_ms")
                                .put("value", CanonicalValue.of(812)).build())),
                Fixtures.ACTOR).address().orElseThrow();
        return obs.revision();
    }

    private static Revision claim(CanonicalValue payload, List<Ref> refs) {
        return Revision.of(Kind.CLAIM, "regression-verdict", payload, refs);
    }

    private static CanonicalValue lawfulPayload() {
        return CanonicalValue.objectBuilder()
                .put("statement", "p95 latency regressed")
                .put("method", "welch-t-test:v2")
                .put("confidence", CanonicalValue.of(new BigDecimal("0.87")))
                .build();
    }

    @Test
    @DisplayName("a lawful claim (statement + method + confidence + evidence) is appendable")
    void lawfulClaimAppendable() {
        ForgeKernel k = Fixtures.kernel();
        RevisionHash evidence = anObservation(k);
        Address.Revision addr = (Address.Revision) k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(claim(lawfulPayload(), List.of(Ref.of(CITES, evidence)))),
                Fixtures.ACTOR).address().orElseThrow();
        assertEquals(Kind.CLAIM, addr.kind());
        assertTrue(k.revision(addr.revision()).isPresent());
    }

    @Test
    @DisplayName("a naked-number claim (no statement/method/confidence/evidence) is unappendable")
    void nakedNumberRejected() {
        ForgeKernel k = Fixtures.kernel();
        KernelException ex = assertThrows(KernelException.class, () -> k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(Revision.leaf(Kind.CLAIM, "kpi", CanonicalValue.of(42))),
                Fixtures.ACTOR));
        assertEquals(KernelException.Reason.CLAIM_LAW, ex.reason());
    }

    @Test
    @DisplayName("a claim without evidence references is rejected")
    void withoutEvidenceRejected() {
        ForgeKernel k = Fixtures.kernel();
        KernelException ex = assertThrows(KernelException.class, () -> k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(claim(lawfulPayload(), List.of())), Fixtures.ACTOR));
        assertEquals(KernelException.Reason.CLAIM_LAW, ex.reason());
    }

    @Test
    @DisplayName("a claim without a named method is rejected")
    void withoutMethodRejected() {
        ForgeKernel k = Fixtures.kernel();
        RevisionHash evidence = anObservation(k);
        CanonicalValue noMethod = CanonicalValue.objectBuilder()
                .put("statement", "s").put("confidence", CanonicalValue.of(new BigDecimal("0.5"))).build();
        KernelException ex = assertThrows(KernelException.class, () -> k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(claim(noMethod, List.of(Ref.of(CITES, evidence)))), Fixtures.ACTOR));
        assertEquals(KernelException.Reason.CLAIM_LAW, ex.reason());
    }

    @Test
    @DisplayName("a claim with confidence outside [0,1] is rejected")
    void confidenceOutOfRangeRejected() {
        ForgeKernel k = Fixtures.kernel();
        RevisionHash evidence = anObservation(k);
        CanonicalValue bad = CanonicalValue.objectBuilder()
                .put("statement", "s").put("method", "m:v1")
                .put("confidence", CanonicalValue.of(new BigDecimal("1.5"))).build();
        KernelException ex = assertThrows(KernelException.class, () -> k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(claim(bad, List.of(Ref.of(CITES, evidence)))), Fixtures.ACTOR));
        assertEquals(KernelException.Reason.CLAIM_LAW, ex.reason());
    }

    @Test
    @DisplayName("the Claim Law is also enforced when a new revision is added to a claim")
    void enforcedOnAddRevision() {
        ForgeKernel k = Fixtures.kernel();
        RevisionHash evidence = anObservation(k);
        Address.Revision v1 = (Address.Revision) k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(claim(lawfulPayload(), List.of(Ref.of(CITES, evidence)))),
                Fixtures.ACTOR).address().orElseThrow();
        // A superseding revision that is a naked number must also be rejected.
        KernelException ex = assertThrows(KernelException.class, () -> k.append(Fixtures.ORG,
                new AppendCommand.AddRevision(v1.node(), Revision.leaf(Kind.CLAIM, "regression-verdict",
                        CanonicalValue.of(7))), Fixtures.ACTOR));
        assertEquals(KernelException.Reason.CLAIM_LAW, ex.reason());
    }
}
