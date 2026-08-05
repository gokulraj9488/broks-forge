package com.broksforge.kernel.core.store;

import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;

import java.util.Optional;

/**
 * The content-addressed revision store — a projection of the log's {@code NodePut} facts.
 *
 * <p>Keyed by {@link RevisionHash}, it deduplicates identical content (Law 3): byte-identical
 * revisions are stored once regardless of how many facts assert them. It holds no truth of its own;
 * it is rebuilt by folding the log.
 */
public interface RevisionStore {

    /**
     * Stores a revision under its content hash. Idempotent: storing content that is already present
     * is a no-op (the hashes, hence the content, are identical).
     *
     * @param hash     the revision's content hash (must equal {@code revision.hash()})
     * @param revision the revision
     */
    void put(RevisionHash hash, Revision revision);

    /**
     * @param hash the content hash
     * @return the revision, if present
     */
    Optional<Revision> get(RevisionHash hash);

    /**
     * @param hash the content hash
     * @return true if a revision with this hash is present
     */
    boolean contains(RevisionHash hash);
}
