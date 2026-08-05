package com.broksforge.kernel.core.node;

import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.engine.KernelException;

import java.math.BigDecimal;

/**
 * The four kinds' construction-time laws of revision — enforced as physics at append time.
 *
 * <p>MANIFESTO Article V opens: "Laws are enforced by the substrate: violating appends are
 * unrepresentable, not flagged." Two of the ten laws are construction-time invariants on a revision's
 * shape and can only be checked when a node is appended:
 *
 * <ul>
 *   <li><b>Law 5 — the Claim Law</b> (ADR-V2-0003, DOMAIN_MODEL §3.3): a {@link
 *       com.broksforge.kernel.api.Kind#CLAIM} is <em>unappendable without</em> a statement, a named
 *       method, a calibrated confidence in {@code [0,1]}, and at least one evidence reference. Since
 *       every derived number is a claim, this is what makes "no unexplained number can exist anywhere
 *       in Forge" true by construction — the way Git cannot store a commit without a tree.</li>
 *   <li><b>Law 6 — the Decision Law</b> (ADR-V2-0004, DOMAIN_MODEL §3.4): a {@link
 *       com.broksforge.kernel.api.Kind#DECISION} must cite the claims it rests on (an
 *       {@link EdgeFamily#INTENT} reference) or explicitly declare itself a judgment call. Honesty
 *       outranks ceremony — the escape hatch is deliberate.</li>
 * </ul>
 *
 * <p>{@link com.broksforge.kernel.api.Kind#ARTIFACT} and {@link com.broksforge.kernel.api.Kind#OBSERVATION}
 * have no construction-time invariant here — their laws of revision are behavioral (supersession /
 * annotation) and are expressed by the append-only command set, not by a shape check.
 *
 * <p>This validator is a pure function of a {@link Revision}: it inspects the revision's payload and
 * intrinsic references only. It is invoked by the append engine for {@code CreateNode} and
 * {@code AddRevision}; it is intentionally <em>not</em> applied during log replay, so a log written
 * before this law shipped still folds back into projections unchanged (the log is truth; laws gate new
 * writes, not history).
 */
public final class KindLaws {

    /** Reserved claim payload key: what is believed (DOMAIN_MODEL §3.3.1). */
    public static final String CLAIM_STATEMENT = "statement";
    /** Reserved claim payload key: the named, versioned procedure that produced it (§3.3.2). */
    public static final String CLAIM_METHOD = "method";
    /** Reserved claim payload key: a calibrated value in [0,1] (§3.3.4). */
    public static final String CLAIM_CONFIDENCE = "confidence";
    /** Reserved decision payload key: an explicit judgment-call self-declaration (§3.4, Law 6). */
    public static final String DECISION_JUDGMENT_CALL = "judgment-call";

    private KindLaws() {
    }

    /**
     * Enforces the construction-time law for the revision's kind.
     *
     * @param revision the revision being appended
     * @throws KernelException {@code CLAIM_LAW} / {@code DECISION_LAW} if the kind's law is violated
     */
    public static void enforce(Revision revision) {
        switch (revision.kind()) {
            case CLAIM -> enforceClaimLaw(revision);
            case DECISION -> enforceDecisionLaw(revision);
            case ARTIFACT, OBSERVATION -> {
                // No construction-time invariant: these kinds' laws of revision are behavioral.
            }
        }
    }

    private static void enforceClaimLaw(Revision r) {
        if (!(r.payload() instanceof CanonicalValue.Obj obj)) {
            throw claimLaw("a claim payload must be an object carrying "
                    + CLAIM_STATEMENT + ", " + CLAIM_METHOD + ", and " + CLAIM_CONFIDENCE);
        }
        requireNonBlankString(obj, CLAIM_STATEMENT, "a statement");
        requireNonBlankString(obj, CLAIM_METHOD, "a named method");
        CanonicalValue confidence = obj.entries().get(CLAIM_CONFIDENCE);
        if (!(confidence instanceof CanonicalValue.Num num)) {
            throw claimLaw("a claim must carry a numeric '" + CLAIM_CONFIDENCE + "' in [0,1]");
        }
        BigDecimal v = num.value();
        if (v.signum() < 0 || v.compareTo(BigDecimal.ONE) > 0) {
            throw claimLaw("claim confidence must be calibrated within [0,1], was " + v.toPlainString());
        }
        if (!hasReferenceInFamily(r, EdgeFamily.EVIDENCE)) {
            throw claimLaw("a claim cannot exist without at least one evidence reference");
        }
    }

    private static void enforceDecisionLaw(Revision r) {
        if (hasReferenceInFamily(r, EdgeFamily.INTENT)) {
            return; // cites the claims it rests on, via an intent-family reference
        }
        if (r.payload() instanceof CanonicalValue.Obj obj
                && obj.entries().get(DECISION_JUDGMENT_CALL) instanceof CanonicalValue.Bool b
                && b.value()) {
            return; // explicitly self-declared as a judgment call
        }
        throw new KernelException(KernelException.Reason.DECISION_LAW,
                "a decision must cite the claims it rests on (an intent-family reference) or declare '"
                        + DECISION_JUDGMENT_CALL + "': true");
    }

    private static boolean hasReferenceInFamily(Revision r, EdgeFamily family) {
        for (Ref ref : r.refs()) {
            if (ref.family() == family) {
                return true;
            }
        }
        return false;
    }

    private static void requireNonBlankString(CanonicalValue.Obj obj, String key, String what) {
        if (!(obj.entries().get(key) instanceof CanonicalValue.Str s) || s.value().isBlank()) {
            throw claimLaw("a claim must carry " + what + " (payload field '" + key + "')");
        }
    }

    private static KernelException claimLaw(String message) {
        return new KernelException(KernelException.Reason.CLAIM_LAW, message);
    }
}
