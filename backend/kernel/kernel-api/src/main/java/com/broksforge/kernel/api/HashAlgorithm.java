package com.broksforge.kernel.api;

/**
 * The content-hash algorithms the kernel understands.
 *
 * <p>{@link RevisionHash} carries an explicit algorithm tag (multihash-style) so the hash
 * function can migrate a decade out without any ambiguity about how an existing hash was
 * computed. Today there is exactly one: {@link #SHA_256}.
 *
 * <p>Implements Law 3 (Content addressing); justified by docs/v2/KERNEL_IMPLEMENTATION_PLAN.md §7.
 */
public enum HashAlgorithm {

    /** SHA-256: 32-byte digest, 64 lower-case hex characters. */
    SHA_256("sha-256", "SHA-256", 32);

    private final String wireName;
    private final String jcaName;
    private final int digestLengthBytes;

    HashAlgorithm(String wireName, String jcaName, int digestLengthBytes) {
        this.wireName = wireName;
        this.jcaName = jcaName;
        this.digestLengthBytes = digestLengthBytes;
    }

    /** @return the stable token used in a {@link RevisionHash} string (e.g. {@code "sha-256"}) */
    public String wireName() {
        return wireName;
    }

    /** @return the {@link java.security.MessageDigest} algorithm name (e.g. {@code "SHA-256"}) */
    public String jcaName() {
        return jcaName;
    }

    /** @return the digest length in bytes */
    public int digestLengthBytes() {
        return digestLengthBytes;
    }

    /** @return the digest length in lower-case hex characters (two per byte) */
    public int hexLength() {
        return digestLengthBytes * 2;
    }

    /**
     * Parses a wire name (e.g. {@code "sha-256"}) into an algorithm.
     *
     * @param wireName the algorithm token
     * @return the matching algorithm
     * @throws IllegalArgumentException if the token is null or unknown
     */
    public static HashAlgorithm fromWireName(String wireName) {
        if (wireName == null) {
            throw new IllegalArgumentException("hash algorithm wire name must not be null");
        }
        for (HashAlgorithm a : values()) {
            if (a.wireName.equals(wireName)) {
                return a;
            }
        }
        throw new IllegalArgumentException("unknown hash algorithm: " + wireName);
    }
}
