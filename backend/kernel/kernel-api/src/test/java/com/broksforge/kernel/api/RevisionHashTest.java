package com.broksforge.kernel.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Value-type tests for {@link RevisionHash} and {@link HashAlgorithm} — the content-derived "which
 * state?" identity (DOMAIN_MODEL §1.2). The multihash-style textual form must round-trip exactly,
 * because it appears inside {@link Address} URIs that are required to be stable forever.
 */
class RevisionHashTest {

    private static byte[] digest(int fill) {
        byte[] b = new byte[32];
        java.util.Arrays.fill(b, (byte) fill);
        return b;
    }

    @Test
    @DisplayName("builds from a digest and formats as '<algorithm>:<hex>'")
    void ofAndToString() {
        RevisionHash h = RevisionHash.of(HashAlgorithm.SHA_256, digest(0x00));
        assertEquals("sha-256:" + "00".repeat(32), h.toString());
        assertEquals(HashAlgorithm.SHA_256, h.algorithm());
        assertEquals("00".repeat(32), h.hex());
    }

    @Test
    @DisplayName("parse round-trips toString")
    void parseRoundTrip() {
        RevisionHash h = RevisionHash.of(HashAlgorithm.SHA_256, digest(0xAB));
        assertEquals(h, RevisionHash.parse(h.toString()));
    }

    @Test
    @DisplayName("digestBytes returns a copy equal to the input")
    void digestBytesCopy() {
        byte[] in = digest(0x7F);
        RevisionHash h = RevisionHash.of(HashAlgorithm.SHA_256, in);
        byte[] out = h.digestBytes();
        assertEquals(32, out.length);
        out[0] = 0x00; // mutating the copy must not affect the hash
        assertEquals("7f".repeat(32), h.hex());
    }

    @Test
    @DisplayName("wrong digest length is rejected")
    void wrongLength() {
        assertThrows(IllegalArgumentException.class,
                () -> RevisionHash.of(HashAlgorithm.SHA_256, new byte[16]));
    }

    @Test
    @DisplayName("malformed textual forms are rejected")
    void malformed() {
        assertThrows(IllegalArgumentException.class, () -> RevisionHash.parse(null));
        assertThrows(IllegalArgumentException.class, () -> RevisionHash.parse("nocolon"));
        assertThrows(IllegalArgumentException.class, () -> RevisionHash.parse("md5:abcd"));
        assertThrows(IllegalArgumentException.class, () -> RevisionHash.parse("sha-256:tooshort"));
        // Upper-case hex is not canonical.
        assertThrows(IllegalArgumentException.class, () -> RevisionHash.parse("sha-256:" + "AB".repeat(32)));
    }

    @Test
    @DisplayName("equality is by algorithm and digest")
    void equality() {
        assertEquals(
                RevisionHash.of(HashAlgorithm.SHA_256, digest(0x11)),
                RevisionHash.of(HashAlgorithm.SHA_256, digest(0x11)));
        assertNotEquals(
                RevisionHash.of(HashAlgorithm.SHA_256, digest(0x11)),
                RevisionHash.of(HashAlgorithm.SHA_256, digest(0x22)));
    }

    @Test
    @DisplayName("hash algorithm metadata is correct")
    void algorithmMetadata() {
        assertEquals("sha-256", HashAlgorithm.SHA_256.wireName());
        assertEquals("SHA-256", HashAlgorithm.SHA_256.jcaName());
        assertEquals(32, HashAlgorithm.SHA_256.digestLengthBytes());
        assertEquals(64, HashAlgorithm.SHA_256.hexLength());
        assertEquals(HashAlgorithm.SHA_256, HashAlgorithm.fromWireName("sha-256"));
        assertThrows(IllegalArgumentException.class, () -> HashAlgorithm.fromWireName("sha-1"));
    }
}
