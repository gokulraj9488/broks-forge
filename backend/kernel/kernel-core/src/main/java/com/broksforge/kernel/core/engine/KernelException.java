package com.broksforge.kernel.core.engine;

/**
 * Signals a rejected append — an attempt to write a fact that a kernel law or precondition forbids.
 * Because the kernel makes illegal state unrepresentable wherever possible, this exception covers the
 * cases that can only be checked at append time (a reference to content that does not exist yet, a
 * compare-and-swap that lost a race, a revision of an unknown continuant).
 *
 * <p>It is unchecked: a caller that hits one has made a programming or concurrency error, not a
 * recoverable I/O condition. The {@link Reason} lets callers branch (for example, retry on
 * {@link Reason#CAS_FAILURE}).
 */
public class KernelException extends RuntimeException {

    /** Why an append was rejected. */
    public enum Reason {
        /** An intrinsic reference targets a revision that does not exist (would break closure). */
        MISSING_REFERENCE,
        /** A revision was added to a continuant that does not exist. */
        UNKNOWN_NODE,
        /** A revision's kind does not match the continuant's established kind. */
        KIND_MISMATCH,
        /** An edge endpoint or name target does not exist. */
        MISSING_TARGET,
        /** A name repointing's expected current target did not match (lost the race). */
        CAS_FAILURE,
        /** A referenced revision does not exist. */
        UNKNOWN_REVISION,
        /**
         * A claim revision violates the Claim Law (MANIFESTO Law 5, ADR-V2-0003): it lacks a
         * statement, a named method, a calibrated confidence in [0,1], or at least one evidence
         * reference. A lawless claim is unappendable — "no unexplained number can exist anywhere."
         */
        CLAIM_LAW,
        /**
         * A decision revision violates the Decision Law (MANIFESTO Law 6, ADR-V2-0004): it neither
         * cites the claims it rests on (an intent-family reference) nor declares itself a judgment
         * call.
         */
        DECISION_LAW
    }

    private final Reason reason;

    /**
     * @param reason  the machine-readable reason
     * @param message the human-readable detail
     */
    public KernelException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** @return the machine-readable reason */
    public Reason reason() {
        return reason;
    }
}
