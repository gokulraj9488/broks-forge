package com.broksforge.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Cryptographic helpers for opaque secrets (refresh tokens, password-reset and
 * e-mail-verification tokens, and API key secrets).
 *
 * <p>Secrets are generated from a {@link SecureRandom} and persisted only as
 * SHA-256 hashes. Because these values are high-entropy (256 bits), a fast hash
 * is appropriate and lookups remain O(1); BCrypt is reserved for low-entropy
 * user passwords.</p>
 */
public final class SecureTokens {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int DEFAULT_TOKEN_BYTES = 32; // 256 bits

    private SecureTokens() {
    }

    /**
     * @return a URL-safe, 256-bit random token
     */
    public static String generateToken() {
        return generateToken(DEFAULT_TOKEN_BYTES);
    }

    public static String generateToken(int numBytes) {
        byte[] bytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /**
     * @return a lowercase hex string of {@code numBytes} random bytes
     *         (useful for short, human-readable public identifiers)
     */
    public static String randomHex(int numBytes) {
        byte[] bytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * @return the lowercase hex SHA-256 digest of {@code value}
     */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS to be present on every JVM.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Constant-time comparison of two hashes to avoid timing side channels.
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
