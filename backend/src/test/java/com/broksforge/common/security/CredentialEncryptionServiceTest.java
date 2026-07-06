package com.broksforge.common.security;

import com.broksforge.common.exception.ApiException;
import com.broksforge.config.properties.EncryptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CredentialEncryptionService (AES-256-GCM)")
class CredentialEncryptionServiceTest {

    // Test-only Base64 32-byte key (AES-256). Not a real key.
    private static final String KEY = "bmsy5fVfCCs4bmfYnV5sGmdlh3edbbnP3vZxvX2fchI=";

    private CredentialEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new CredentialEncryptionService(new EncryptionProperties(KEY, 1));
    }

    @Test
    @DisplayName("round-trips a secret through encrypt/decrypt")
    void roundTrips() {
        String secret = "gsk_super_secret_value_1234567890";
        String cipher = service.encrypt(secret);
        assertThat(cipher).isNotNull().doesNotContain(secret);
        assertThat(service.decrypt(cipher)).isEqualTo(secret);
    }

    @Test
    @DisplayName("ciphertext is versioned and non-deterministic (fresh IV per call)")
    void ciphertextIsVersionedAndRandom() {
        String a = service.encrypt("same");
        String b = service.encrypt("same");
        assertThat(a).startsWith("v1:");
        assertThat(a).isNotEqualTo(b);            // random IV → different ciphertext
        assertThat(service.decrypt(a)).isEqualTo(service.decrypt(b));
        assertThat(service.currentKeyVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("passes null through transparently")
    void handlesNull() {
        assertThat(service.encrypt(null)).isNull();
        assertThat(service.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("rejects malformed or tampered ciphertext without leaking details")
    void rejectsTampered() {
        assertThatThrownBy(() -> service.decrypt("not-a-valid-ciphertext"))
                .isInstanceOf(ApiException.class);
        String valid = service.encrypt("value");
        String tampered = valid.substring(0, valid.length() - 2) + "AA";
        assertThatThrownBy(() -> service.decrypt(tampered)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("rejects a key that is not 16/24/32 bytes")
    void rejectsWrongKeySize() {
        assertThatThrownBy(() -> new CredentialEncryptionService(
                new EncryptionProperties("c2hvcnQ=", 1)))   // "short" → 5 bytes
                .isInstanceOf(IllegalStateException.class);
    }
}
