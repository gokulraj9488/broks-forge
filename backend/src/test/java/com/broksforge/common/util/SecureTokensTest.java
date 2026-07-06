package com.broksforge.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecureTokens")
class SecureTokensTest {

    @Test
    @DisplayName("generates high-entropy, URL-safe tokens that differ each time")
    void generatesUniqueTokens() {
        String a = SecureTokens.generateToken();
        String b = SecureTokens.generateToken();
        assertThat(a).isNotBlank().doesNotContain("+", "/", "=");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("numeric codes are zero-padded to the requested length")
    void numericCodeIsPadded() {
        for (int i = 0; i < 200; i++) {
            assertThat(SecureTokens.generateNumericCode(6)).matches("\\d{6}");
        }
    }

    @Test
    @DisplayName("rejects an out-of-range digit count")
    void rejectsBadDigitCount() {
        assertThatThrownBy(() -> SecureTokens.generateNumericCode(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecureTokens.generateNumericCode(10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SHA-256 hex is deterministic, 64 chars, and differs for different input")
    void sha256IsDeterministic() {
        String hash = SecureTokens.sha256Hex("hello");
        assertThat(hash).hasSize(64).isEqualTo(SecureTokens.sha256Hex("hello"));
        assertThat(hash).isNotEqualTo(SecureTokens.sha256Hex("hell0"));
    }

    @Test
    @DisplayName("constant-time equality compares values and null-guards")
    void constantTimeEquals() {
        assertThat(SecureTokens.constantTimeEquals("abc", "abc")).isTrue();
        assertThat(SecureTokens.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(SecureTokens.constantTimeEquals(null, "abc")).isFalse();
        assertThat(SecureTokens.constantTimeEquals("abc", null)).isFalse();
    }
}
