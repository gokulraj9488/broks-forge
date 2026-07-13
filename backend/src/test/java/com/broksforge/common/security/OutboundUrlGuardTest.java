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

    @Test
    @DisplayName("trusts a native Ollama provider on host.docker.internal without any global opt-in")
    void trustsOllamaOnDockerHost() {
        assertThat(guard.check("http://host.docker.internal:11434/api/tags", false, true).allowed()).isTrue();
    }

    @Test
    @DisplayName("trusts a native Ollama provider on localhost without any global opt-in")
    void trustsOllamaOnLocalhost() {
        assertThat(guard.check("http://localhost:11434/api/tags", false, true).allowed()).isTrue();
    }

    @Test
    @DisplayName("trusts a native Ollama provider on 127.0.0.1 without any global opt-in")
    void trustsOllamaOnLoopbackIp() {
        assertThat(guard.check("http://127.0.0.1:11434/api/tags", false, true).allowed()).isTrue();
    }

    @Test
    @DisplayName("a Custom REST provider pointing at localhost is still blocked (bypass is Ollama-only)")
    void doesNotTrustNonOllamaOnLocalhost() {
        assertThat(guard.check("http://localhost:9000/invoke", false, false).allowed()).isFalse();
    }

    @Test
    @DisplayName("the Ollama bypass never widens to arbitrary private/link-local targets")
    void trustedOllamaFlagDoesNotWidenToOtherPrivateHosts() {
        assertThat(guard.check("http://10.0.0.5/api/tags", false, true).allowed()).isFalse();
        assertThat(guard.check("http://169.254.169.254/latest/meta-data", false, true).allowed()).isFalse();
    }

    @Test
    @DisplayName("remote hosted providers (OpenAI/Groq/OpenRouter/etc.) are unaffected by the Ollama flag")
    void remoteProvidersUnaffectedByOllamaFlag() {
        assertThat(guard.check("https://api.openai.com/v1/models", false, false).allowed()).isTrue();
        assertThat(guard.check("https://api.groq.com/openai/v1/models", false, true).allowed()).isTrue();
    }
}
