package com.broksforge.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Symmetric-encryption configuration bound from
 * {@code broksforge.security.encryption.*}.
 *
 * <p>Used by {@code CredentialEncryptionService} to encrypt agent credentials
 * at rest. The secret must be a Base64-encoded 256-bit (32-byte) key for
 * AES-256-GCM.</p>
 *
 * @param secret     Base64-encoded 32-byte AES key
 * @param keyVersion identifier of the active key, stamped onto every ciphertext
 *                   so keys can be rotated without losing the ability to decrypt
 *                   historical values
 */
@Validated
@ConfigurationProperties(prefix = "broksforge.security.encryption")
public record EncryptionProperties(

        @NotBlank(message = "broksforge.security.encryption.secret must be provided")
        String secret,

        @Positive
        int keyVersion
) {
}
