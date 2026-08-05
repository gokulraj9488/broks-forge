package com.broksforge.kernel.api;

import java.util.HexFormat;
import java.util.Objects;

/**
 * The identity of a revision — the content-derived hash of one immutable state.
 *
 * <p>A {@code RevisionHash} is the Merkle identity of a revision: the hash of its canonical
 * content, which itself includes the hashes of everything the revision references. From this one
 * property flow deduplication (equal content ⇒ equal hash ⇒ stored once), O(1) equality,
 * structural diff, and the closure hash as a reproducibility certificate (Law 3;
 * docs/v2/DOMAIN_MODEL.md §1.2).
 *
 * <p>The value is stored as an algorithm tag plus a lower-case hex digest, and its canonical
 * string form is {@code "<algorithm>:<hex>"} — for example
 * {@code "sha-256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"}. Storing the
 * digest as hex (rather than a {@code byte[]}) gives this type clean value semantics and a stable
 * textual form.
 *
 * <p>This class is immutable and final.
 */
public final class RevisionHash {

    private static final HexFormat HEX = HexFormat.of(); // lower-case, no delimiters

    private final HashAlgorithm algorithm;
    private final String hex;

    private RevisionHash(HashAlgorithm algorithm, String hex) {
        this.algorithm = algorithm;
        this.hex = hex;
    }

    /**
     * Builds a revision hash from a raw digest.
     *
     * @param algorithm the algorithm that produced {@code digest}
     * @param digest    the raw digest bytes; length must match the algorithm
     * @return the revision hash
     * @throws IllegalArgumentException if arguments are null or the length is wrong
     */
    public static RevisionHash of(HashAlgorithm algorithm, byte[] digest) {
        if (algorithm == null) {
            throw new IllegalArgumentException("hash algorithm must not be null");
        }
        if (digest == null) {
            throw new IllegalArgumentException("digest must not be null");
        }
        if (digest.length != algorithm.digestLengthBytes()) {
            throw new IllegalArgumentException(
                    "digest length " + digest.length + " does not match " + algorithm.wireName()
                            + " (" + algorithm.digestLengthBytes() + " bytes)");
        }
        return new RevisionHash(algorithm, HEX.formatHex(digest));
    }

    /**
     * Parses the canonical string form {@code "<algorithm>:<hex>"}.
     *
     * @param s the text
     * @return the parsed revision hash
     * @throws IllegalArgumentException if the text is null or malformed
     */
    public static RevisionHash parse(String s) {
        if (s == null) {
            throw new IllegalArgumentException("revision hash text must not be null");
        }
        int colon = s.indexOf(':');
        if (colon <= 0 || colon == s.length() - 1) {
            throw new IllegalArgumentException("revision hash must be '<algorithm>:<hex>': " + s);
        }
        HashAlgorithm algorithm = HashAlgorithm.fromWireName(s.substring(0, colon));
        String hex = s.substring(colon + 1);
        if (hex.length() != algorithm.hexLength()) {
            throw new IllegalArgumentException(
                    "hex length " + hex.length() + " does not match " + algorithm.wireName()
                            + " (" + algorithm.hexLength() + " chars)");
        }
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean lowerHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!lowerHex) {
                throw new IllegalArgumentException("revision hash must be lower-case hex: " + s);
            }
        }
        return new RevisionHash(algorithm, hex);
    }

    /** @return the algorithm */
    public HashAlgorithm algorithm() {
        return algorithm;
    }

    /** @return the lower-case hex digest (without the algorithm prefix) */
    public String hex() {
        return hex;
    }

    /** @return a fresh copy of the raw digest bytes */
    public byte[] digestBytes() {
        return HEX.parseHex(hex);
    }

    /** @return the canonical string form {@code "<algorithm>:<hex>"} */
    @Override
    public String toString() {
        return algorithm.wireName() + ":" + hex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RevisionHash other)) {
            return false;
        }
        return algorithm == other.algorithm && hex.equals(other.hex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(algorithm, hex);
    }
}
