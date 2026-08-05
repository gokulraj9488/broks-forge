package com.broksforge.kernel.core.memory;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.kernel.core.store.NameStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link NameStore}: the current target of each name, folded from {@code NameRepointed}
 * facts. Historical resolution is the engine's job (log replay), so this projection holds only the
 * present value. Guarded by the instance monitor.
 */
public final class InMemoryNameStore implements NameStore {

    private final Map<OrgId, Map<Name, Address.Revision>> current = new HashMap<>();

    @Override
    public synchronized void apply(LogEntry entry) {
        if (entry.payload() instanceof Payload.NameRepointed nr) {
            current.computeIfAbsent(entry.org(), o -> new HashMap<>()).put(nr.name(), nr.to());
        }
    }

    @Override
    public synchronized Optional<Address.Revision> current(OrgId org, Name name) {
        return Optional.ofNullable(current.getOrDefault(org, Map.of()).get(name));
    }
}
