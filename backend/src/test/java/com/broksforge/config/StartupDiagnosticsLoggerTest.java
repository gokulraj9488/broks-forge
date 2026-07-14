package com.broksforge.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StartupDiagnosticsLoggerTest {

    @Test
    void returnsNullForNullUrl() {
        assertThat(StartupDiagnosticsLogger.maskCredentials(null)).isNull();
    }

    @Test
    void leavesACredentialFreeUrlUnchanged() {
        String url = "jdbc:postgresql://autorack.proxy.rlwy.net:12345/railway";
        assertThat(StartupDiagnosticsLogger.maskCredentials(url)).isEqualTo(url);
    }

    @Test
    void redactsUserInfoEmbeddedInTheAuthority() {
        String url = "jdbc:postgresql://postgres:s3cr3t@autorack.proxy.rlwy.net:12345/railway";
        String masked = StartupDiagnosticsLogger.maskCredentials(url);
        assertThat(masked).isEqualTo("jdbc:postgresql://***@autorack.proxy.rlwy.net:12345/railway");
        assertThat(masked).doesNotContain("s3cr3t");
    }

    @Test
    void redactsACredentialBearingQueryParameter() {
        String url = "jdbc:postgresql://host:5432/db?password=s3cr3t&sslmode=require";
        String masked = StartupDiagnosticsLogger.maskCredentials(url);
        assertThat(masked).isEqualTo("jdbc:postgresql://host:5432/db?password=***&sslmode=require");
        assertThat(masked).doesNotContain("s3cr3t");
    }
}
