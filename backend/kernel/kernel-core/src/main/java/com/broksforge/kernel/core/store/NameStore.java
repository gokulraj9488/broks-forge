package com.broksforge.kernel.core.store;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.core.log.LogEntry;

import java.util.Optional;

/**
 * The current-name projection — the fast path for resolving a name at HEAD.
 *
 * <p>Names are the only mutable state (ADR-V2-0006); this projection holds each name's current
 * target by folding {@code NameRepointed} facts. Historical resolution ({@code resolve} at a past
 * position) is done by the engine replaying the log, not from this projection.
 */
public interface NameStore {

    /**
     * Folds one committed entry into the projection.
     *
     * @param entry the entry
     */
    void apply(LogEntry entry);

    /**
     * @param org  the organization
     * @param name the name
     * @return the current target the name points to, if it exists
     */
    Optional<Address.Revision> current(OrgId org, Name name);
}
