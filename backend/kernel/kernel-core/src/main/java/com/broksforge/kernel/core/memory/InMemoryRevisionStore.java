package com.broksforge.kernel.core.memory;

import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.store.RevisionStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link RevisionStore}: a content-addressed map keyed by {@link RevisionHash}. Because
 * revisions are immutable values keyed by content, storage is naturally deduplicating and
 * thread-safe with a concurrent map.
 */
public final class InMemoryRevisionStore implements RevisionStore {

    private final Map<RevisionHash, Revision> byHash = new ConcurrentHashMap<>();

    @Override
    public void put(RevisionHash hash, Revision revision) {
        if (hash == null || revision == null) {
            throw new IllegalArgumentException("hash and revision must not be null");
        }
        if (!revision.hash().equals(hash)) {
            throw new IllegalArgumentException("hash does not match revision content");
        }
        byHash.putIfAbsent(hash, revision);
    }

    @Override
    public Optional<Revision> get(RevisionHash hash) {
        return Optional.ofNullable(byHash.get(hash));
    }

    @Override
    public boolean contains(RevisionHash hash) {
        return byHash.containsKey(hash);
    }
}
