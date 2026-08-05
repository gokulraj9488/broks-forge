package com.broksforge.platform;

import com.broksforge.config.properties.PlatformProperties;
import com.broksforge.fxp.PlatformHealth;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 acceptance: with the platform enabled, the context boots (which itself proves the fail-fast startup
 * health check passed), the {@code forge_kernel} schema and kernel migrations exist, and the platform
 * reports healthy with a valid chain — all without touching the application's own schema.
 */
class ForgePlatformInitializationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ForgePlatform platform;

    @Autowired
    private PlatformProperties properties;

    @Autowired
    private DataSource dataSource;

    @Test
    void forgeKernelSchemaAndMigrationsExist() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            try (ResultSet rs = statement.executeQuery(
                    "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'forge_kernel'")) {
                assertTrue(rs.next(), "forge_kernel schema must exist");
            }

            try (ResultSet rs = statement.executeQuery(
                    "SELECT to_regclass('forge_kernel.kernel_schema_history')")) {
                assertTrue(rs.next());
                assertNotNull(rs.getString(1), "kernel migrations must have run inside forge_kernel");
            }
        }
    }

    @Test
    void platformReportsHealthyAndChainVerifies() {
        OrgId probeOrg = OrgId.of(UUID.fromString(properties.probeOrgId()));
        PlatformHealth health = platform.health(probeOrg);
        assertTrue(health.chainValid(), "verifyChain must return true");
        assertTrue(health.integrity().clean(), "integrity scan must be clean");
        assertTrue(health.healthy(), "PlatformHealth must be healthy");
    }
}
