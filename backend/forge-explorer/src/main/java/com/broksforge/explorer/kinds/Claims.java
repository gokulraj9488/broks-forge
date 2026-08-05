package com.broksforge.explorer.kinds;

import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.explorer.Verbs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link Kind#CLAIM} revisions that satisfy the Claim Law.
 *
 * <p>The constitution (MANIFESTO Law 5, ADR-V2-0003) states the Claim Law as physics: "A claim
 * cannot exist without evidence references, a named method, and a calibrated confidence… the end of
 * naked numbers." Phase 1.5 dogfooding found this <em>unenforced</em> in the shipped kernel; the Kernel
 * Amendment Review <b>accepted KAP-1</b> and the kernel now enforces it at append time
 * ({@code com.broksforge.kernel.core.node.KindLaws}): a bare {@code Revision.leaf(Kind.CLAIM, "kpi",
 * Num(42))} is now rejected with {@code KernelException.Reason.CLAIM_LAW}.
 *
 * <p>This builder remains valuable as an ergonomic constructor that <em>fails fast in userspace</em>
 * with clear messages before the append, and that pins the canonical payload shape. Every claim it
 * builds carries a statement, a named+versioned method, a confidence in [0,1], and at least one
 * evidence reference (an {@link Verbs#CITES} ref, evidence family) — matching the kernel contract.
 */
public final class Claims {

    private Claims() {
    }

    /**
     * Builds a lawful claim.
     *
     * @param subtype    the open claim subtype (e.g. {@code regression-verdict}, {@code kpi})
     * @param statement  the claim's assertion in words
     * @param method     the named, versioned method that produced it (e.g. {@code welch-t-test:v2})
     * @param confidence calibrated confidence in [0,1]
     * @param evidence   one or more observation revisions the claim cites; must be non-empty
     * @return the claim revision
     * @throws IllegalArgumentException if any Claim-Law requirement is unmet
     */
    public static Revision claim(String subtype, String statement, String method,
                                 BigDecimal confidence, List<RevisionHash> evidence) {
        requireText(statement, "claim statement");
        requireText(method, "claim method");
        if (confidence == null || confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Claim Law: confidence must be a calibrated value in [0,1]");
        }
        if (evidence == null || evidence.isEmpty()) {
            throw new IllegalArgumentException("Claim Law: a claim cannot exist without evidence references");
        }
        List<Ref> refs = new ArrayList<>(evidence.size());
        for (RevisionHash target : evidence) {
            if (target == null) {
                throw new IllegalArgumentException("Claim Law: evidence reference must not be null");
            }
            refs.add(Ref.of(Verbs.CITES, target));
        }
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("statement", statement)
                .put("method", method)
                .put("confidence", CanonicalValue.of(confidence))
                .build();
        return Revision.of(Kind.CLAIM, subtype, payload, refs);
    }

    private static void requireText(String s, String what) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Claim Law: " + what + " must not be blank");
        }
    }
}
