package com.broksforge.kernel.core.store;

import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.log.LogEntry;

import java.util.List;

/**
 * The append log — the sole source of truth (ADR-V2-0001) and the event stream (ADR-V2-0008).
 *
 * <p>Everything else in the kernel is a projection rebuildable by replaying the log. The log
 * assigns each entry its {@link LogPosition} (gapless, monotonic per org) and chains it to the
 * previous entry's hash; the caller supplies only the entry's content via a {@link Sealer}, so the
 * log controls ordering and the chain atomically.
 *
 * <p>Implementations must guarantee a total order per org. The in-memory backend does so under a
 * per-org monitor; a database backend would use a per-org sequence or serializable transaction.
 */
public interface Log {

    /** Builds the final {@link LogEntry} once the log has assigned its position and previous hash. */
    @FunctionalInterface
    interface Sealer {
        /**
         * @param position the assigned position (1-based)
         * @param prevHash the previous entry's hash, or null for the first entry
         * @return the sealed entry (must carry exactly {@code position} and {@code prevHash})
         */
        LogEntry seal(LogPosition position, RevisionHash prevHash);
    }

    /**
     * Appends one entry atomically, assigning its position and previous-hash.
     *
     * @param org    the organization
     * @param sealer builds the entry from the assigned position and previous hash
     * @return the appended entry
     */
    LogEntry append(OrgId org, Sealer sealer);

    /** @return the current head position for the org ({@link LogPosition#ZERO} if empty) */
    LogPosition head(OrgId org);

    /**
     * Reads entries in {@code (fromExclusive, toInclusive]} order.
     *
     * @param org           the organization
     * @param fromExclusive lower bound, exclusive (use {@link LogPosition#ZERO} for the start)
     * @param toInclusive   upper bound, inclusive
     * @return the entries in ascending position order
     */
    List<LogEntry> read(OrgId org, LogPosition fromExclusive, LogPosition toInclusive);

    /**
     * @param org the organization
     * @return every entry for the org, in ascending position order
     */
    List<LogEntry> all(OrgId org);
}
