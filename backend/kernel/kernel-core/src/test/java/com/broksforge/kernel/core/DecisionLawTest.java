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
 * Law 6 — the Decision Law (MANIFESTO §V.6, ADR-V2-0004, DOMAIN_MODEL §3.4), enforced at append time
 * by {@code KindLaws}. A decision must cite the claims it rests on (an intent-family reference) or
 * explicitly declare itself a judgment call. Honesty outranks ceremony — the escape hatch is deliberate.
 */
class DecisionLawTest {

    private static final Verb CITES = new Verb("cites", EdgeFamily.EVIDENCE);
    private static final Verb RESTS_ON = new Verb("rests_on", EdgeFamily.INTENT);

    private static RevisionHash aClaim(ForgeKernel k) {
        Address.Revision obs = (Address.Revision) k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(Revision.leaf(Kind.OBSERVATION, "metric",
                        CanonicalValue.objectBuilder().put("name", "n").put("value", CanonicalValue.of(1)).build())),
                Fixtures.ACTOR).address().orElseThrow();
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("statement", "s").put("method", "m:v1")
                .put("confidence", CanonicalValue.of(new BigDecimal("0.9"))).build();
        Address.Revision claim = (Address.Revision) k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(Revision.of(Kind.CLAIM, "verdict", payload,
                        List.of(Ref.of(CITES, obs.revision())))), Fixtures.ACTOR).address().orElseThrow();
        return claim.revision();
    }

    @Test
    @DisplayName("a decision that cites a claim (intent-family reference) is appendable")
    void decisionCitingClaimAppendable() {
        ForgeKernel k = Fixtures.kernel();
        RevisionHash claim = aClaim(k);
        Revision decision = Revision.of(Kind.DECISION, "rollback",
                CanonicalValue.objectBuilder().put("statement", "roll back to v1").build(),
                List.of(Ref.of(RESTS_ON, claim)));
        Address.Revision addr = (Address.Revision) k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(decision), Fixtures.ACTOR).address().orElseThrow();
        assertEquals(Kind.DECISION, addr.kind());
        assertTrue(k.revision(addr.revision()).isPresent());
    }

    @Test
    @DisplayName("a decision explicitly declared a judgment call is appendable with no cited claims")
    void judgmentCallAppendable() {
        ForgeKernel k = Fixtures.kernel();
        Revision judgment = Revision.leaf(Kind.DECISION, "accept-risk",
                CanonicalValue.objectBuilder()
                        .put("statement", "ship despite the unknown")
                        .put("judgment-call", true)
                        .put("rationale", "time-boxed spike; risk accepted")
                        .build());
        Address.Revision addr = (Address.Revision) k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(judgment), Fixtures.ACTOR).address().orElseThrow();
        assertEquals(Kind.DECISION, addr.kind());
    }

    @Test
    @DisplayName("a decision that neither cites claims nor declares a judgment call is unappendable")
    void unjustifiedDecisionRejected() {
        ForgeKernel k = Fixtures.kernel();
        Revision unjustified = Revision.leaf(Kind.DECISION, "promotion",
                CanonicalValue.objectBuilder().put("statement", "ship it").build());
        KernelException ex = assertThrows(KernelException.class, () -> k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(unjustified), Fixtures.ACTOR));
        assertEquals(KernelException.Reason.DECISION_LAW, ex.reason());
    }

    @Test
    @DisplayName("declaring 'judgment-call': false without a cited claim does not satisfy the law")
    void judgmentCallFalseRejected() {
        ForgeKernel k = Fixtures.kernel();
        Revision decision = Revision.leaf(Kind.DECISION, "promotion",
                CanonicalValue.objectBuilder().put("statement", "ship it").put("judgment-call", false).build());
        KernelException ex = assertThrows(KernelException.class, () -> k.append(Fixtures.ORG,
                new AppendCommand.CreateNode(decision), Fixtures.ACTOR));
        assertEquals(KernelException.Reason.DECISION_LAW, ex.reason());
    }
}
