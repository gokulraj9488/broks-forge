package com.broksforge.kernel.api.canonical;

import com.broksforge.kernel.api.HashAlgorithm;
import com.broksforge.kernel.api.RevisionHash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes a {@link RevisionHash} from canonical content — the bridge from bytes to identity.
 *
 * <p>This is where content addressing (Law 3) is realized: a value is canonicalized to bytes by
 * {@link CanonicalSerializer} and then hashed. Because canonicalization is deterministic, equal
 * content always yields the same {@code RevisionHash}, which is what makes deduplication and
 * Merkle identity sound.
 *
 * <p>Stateless and final; all methods are pure functions of their input.
 */
public final class ContentHash {

    /** The algorithm used for revision hashes. Fixed today; migratable via the multihash tag. */
    public static final HashAlgorithm ALGORITHM = HashAlgorithm.SHA_256;

    private ContentHash() {
    }

    /**
     * Hashes a canonical value.
     *
     * @param content the value tree
     * @return its revision hash
     */
    public static RevisionHash of(CanonicalValue content) {
        return of(CanonicalSerializer.toBytes(content));
    }

    /**
     * Hashes raw canonical bytes. Exposed so callers that already hold the canonical byte string
     * (for example a store reading persisted content) need not rebuild the value tree.
     *
     * @param canonicalBytes the canonical byte encoding
     * @return the revision hash
     */
    public static RevisionHash of(byte[] canonicalBytes) {
        if (canonicalBytes == null) {
            throw new IllegalArgumentException("canonical bytes must not be null");
        }
        MessageDigest digest = newDigest();
        return RevisionHash.of(ALGORITHM, digest.digest(canonicalBytes));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM.jcaName());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every conformant JDK; its absence is not a recoverable state.
            throw new IllegalStateException("required hash algorithm unavailable: " + ALGORITHM.jcaName(), e);
        }
    }
}
