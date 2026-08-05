package com.broksforge.kernel.core.log;

import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Provenance;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.api.canonical.ContentHash;

/**
 * One immutable, hash-chained fact in an organization's append log.
 *
 * <p>The log is the sole source of truth (ADR-V2-0001) and its own event stream (ADR-V2-0008).
 * Every entry carries: its {@link LogPosition} (the causal clock), the {@link #prevHash()} of the
 * previous entry (the tamper-evident chain), the {@link Provenance} (Law 2 who + Law 8 times), the
 * {@link Payload} (what happened), and this entry's own {@link #entryHash()}.
 *
 * <p>The chain makes append-only <em>cryptographically</em> auditable: each entry commits to its
 * predecessor, so any retroactive edit breaks the chain and is detectable (Certificate-Transparency
 * style). Unlike a {@link RevisionHash} (a content-dedup identity that omits time and actor), the
 * entry hash deliberately includes provenance and position — it identifies the <em>fact</em>, and
 * two facts asserting identical content are correctly distinct.
 *
 * @param org        the organization (graph boundary)
 * @param position   the causal-clock position, unique and gapless within the org
 * @param prevHash   the previous entry's hash, or null for the first entry
 * @param provenance who asserted it and when
 * @param payload    what was asserted
 * @param entryHash  this entry's chain hash (derived; see {@link #seal})
 */
public record LogEntry(
        OrgId org,
        LogPosition position,
        RevisionHash prevHash,
        Provenance provenance,
        Payload payload,
        RevisionHash entryHash) {

    /**
     * @throws IllegalArgumentException if a required component is null
     */
    public LogEntry {
        if (org == null) {
            throw new IllegalArgumentException("entry org must not be null");
        }
        if (position == null) {
            throw new IllegalArgumentException("entry position must not be null");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("entry provenance must not be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("entry payload must not be null");
        }
        if (entryHash == null) {
            throw new IllegalArgumentException("entry hash must not be null");
        }
        // prevHash may be null only at position 1 (the first entry); enforced by the engine.
    }

    /**
     * Seals a new entry: computes its chain hash from its identifying content and returns the
     * complete, immutable {@code LogEntry}. This is the only way to construct a well-formed entry.
     *
     * @param org        the organization
     * @param position   the assigned position
     * @param prevHash   the previous entry's hash (null for the first)
     * @param provenance the provenance
     * @param payload    the payload
     * @return the sealed entry, with {@link #entryHash()} computed
     */
    public static LogEntry seal(OrgId org, LogPosition position, RevisionHash prevHash,
                                Provenance provenance, Payload payload) {
        RevisionHash hash = ContentHash.of(canonicalFor(org, position, prevHash, provenance, payload));
        return new LogEntry(org, position, prevHash, provenance, payload, hash);
    }

    /**
     * Recomputes this entry's hash from its content and checks it matches {@link #entryHash()}.
     * Used by chain verification to detect tampering.
     *
     * @return true if the stored hash equals the recomputed hash
     */
    public boolean verifySelf() {
        RevisionHash recomputed =
                ContentHash.of(canonicalFor(org, position, prevHash, provenance, payload));
        return recomputed.equals(entryHash);
    }

    private static CanonicalValue canonicalFor(OrgId org, LogPosition position, RevisionHash prevHash,
                                               Provenance provenance, Payload payload) {
        CanonicalValue prov = CanonicalValue.objectBuilder()
                .put("actor", provenance.actor().value())
                .put("validTime", provenance.validTime().toString())
                .put("recordTime", provenance.recordTime().toString())
                .build();
        return CanonicalValue.objectBuilder()
                .put("org", org.toString())
                .put("position", position.value())
                .put("prev", prevHash == null ? CanonicalValue.NULL : CanonicalValue.of(prevHash.toString()))
                .put("provenance", prov)
                .put("payload", payload.toCanonical())
                .build();
    }
}
