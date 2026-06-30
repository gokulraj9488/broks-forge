package com.broksforge.common.security;

import com.broksforge.common.exception.ApiException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.config.properties.EncryptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Authenticated symmetric encryption for secrets that must be <em>retrieved</em>
 * later (agent credentials), as opposed to verified-only secrets which are
 * hashed (see {@code SecureTokens}).
 *
 * <p>Algorithm: AES-256-GCM (confidentiality + integrity). Each value is
 * encrypted with a fresh 96-bit IV. The serialized form is self-describing and
 * carries the key version so keys can be rotated without re-encrypting history:</p>
 *
 * <pre>{@code v<keyVersion>:<base64url(iv)>:<base64url(ciphertext+tag)>}</pre>
 *
 * <p>The encryption key is supplied only via the environment and never logged.</p>
 */
@Slf4j
@Service
public class CredentialEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;       // 96-bit IV (GCM recommended)
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String FORMAT_PREFIX = "v";
    private static final String FIELD_SEPARATOR = ":";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    private final SecretKey secretKey;
    private final int keyVersion;

    public CredentialEncryptionService(EncryptionProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.secret());
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException(
                    "broksforge.security.encryption.secret must Base64-decode to 16, 24 or 32 bytes "
                            + "(AES-128/192/256). Generate a 256-bit key with: openssl rand -base64 32");
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        this.keyVersion = properties.keyVersion();
    }

    /**
     * Encrypts a plaintext secret. Returns {@code null} for a {@code null} input
     * so callers can map "no secret" transparently.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return FORMAT_PREFIX + keyVersion + FIELD_SEPARATOR
                    + encoder.encodeToString(iv) + FIELD_SEPARATOR
                    + encoder.encodeToString(ciphertext);
        } catch (Exception e) {
            // Never leak cryptographic details to callers/logs.
            log.error("Credential encryption failed", e);
            throw new ApiException(ErrorCode.CREDENTIAL_ENCRYPTION_ERROR, "Unable to secure credential");
        }
    }

    /**
     * Decrypts a value previously produced by {@link #encrypt(String)}.
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            String[] parts = stored.split(FIELD_SEPARATOR);
            if (parts.length != 3 || !parts[0].startsWith(FORMAT_PREFIX)) {
                throw new IllegalArgumentException("Malformed ciphertext");
            }
            byte[] iv = decoder.decode(parts[1]);
            byte[] ciphertext = decoder.decode(parts[2]);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Credential decryption failed", e);
            throw new ApiException(ErrorCode.CREDENTIAL_ENCRYPTION_ERROR, "Unable to read credential");
        }
    }

    /** @return the active key version stamped onto new ciphertext */
    public int currentKeyVersion() {
        return keyVersion;
    }
}
