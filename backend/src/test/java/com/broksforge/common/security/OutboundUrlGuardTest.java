package com.broksforge.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboundUrlGuard (SSRF defence)")
class OutboundUrlGuardTest {

    private final OutboundUrlGuard guard = new OutboundUrlGuard();

    @Test
    @DisplayName("allows a public https target")
    void allowsPublicTarget() {
        // 8.8.8.8 is a public IP literal (no DNS lookup, deterministic, not private).
        assertThat(guard.check("https://8.8.8.8/openai/v1/models", false).allowed()).isTrue();
    }

    @Test
    @DisplayName("blocks loopback, private and cloud-metadata targets")
    void blocksInternalTargets() {
        assertThat(guard.check("http://127.0.0.1/", false).allowed()).isFalse();
        assertThat(guard.check("http://localhost:8080/", false).allowed()).isFalse();
        assertThat(guard.check("http://10.0.0.5/", false).allowed()).isFalse();
        assertThat(guard.check("http://169.254.169.254/latest/meta-data", false).allowed()).isFalse();
        assertThat(guard.check("http://metadata.google.internal/", false).allowed()).isFalse();
    }

    @Test
    @DisplayName("rejects non-http schemes and embedded credentials")
    void rejectsBadUrls() {
        assertThat(guard.check("ftp://example.com/", false).allowed()).isFalse();
        assertThat(guard.check("https://user:pass@example.com/", false).allowed()).isFalse();
        assertThat(guard.check("not a url", false).allowed()).isFalse();
    }

    @Test
    @DisplayName("permits private targets only when explicitly allowed (dev / local agents)")
    void allowsPrivateWhenOptedIn() {
        assertThat(guard.check("http://127.0.0.1:11434/v1/models", false).allowed()).isFalse();
        assertThat(guard.check("http://127.0.0.1:11434/v1/models", true).allowed()).isTrue();
    }
}
