package com.broksforge.platform;

import com.broksforge.config.properties.PlatformProperties;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.store.postgres.PostgresKernels;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Initializes Platform V2 inside the Spring application (P0 foundation).
 *
 * <p>The kernel's append log is stored in the <b>same PostgreSQL instance</b> but isolated in its own
 * schema (default {@code forge_kernel}) via a small, dedicated Hikari pool. That pool is deliberately
 * <b>not</b> exposed as a Spring {@code DataSource} bean, so Spring Boot's JPA ({@code ddl-auto=validate})
 * and Flyway continue to use the application's primary datasource on the default schema — unchanged.
 *
 * <p><b>Fail-fast:</b> if schema creation, kernel migration, or kernel open fails, bean creation throws and
 * the application does not start. Everything here is gated by {@code broksforge.platform.v2.enabled=true};
 * set it false to run the application exactly as before.
 *
 * <p>Only frozen platform <em>public</em> APIs are used ({@code PostgresKernels}); no internal package is
 * touched and no frozen module is modified.
 */
@Configuration
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
public class ForgePlatformConfig {

    private static final Logger log = LoggerFactory.getLogger(ForgePlatformConfig.class);
    private static final Pattern SCHEMA_NAME = Pattern.compile("^[a-z_][a-z0-9_]*$");

    private HikariDataSource kernelDataSource;

    @Bean
    public ForgeKernel forgeKernel(DataSource primaryDataSource,
                                   DataSourceProperties dataSourceProperties,
                                   PlatformProperties properties) {
        String schema = properties.schema();
        if (!SCHEMA_NAME.matcher(schema).matches()) {
            throw new IllegalStateException("Invalid forge_kernel schema name: '" + schema + "'");
        }
        createSchemaIfMissing(primaryDataSource, schema);
        this.kernelDataSource = buildKernelPool(dataSourceProperties, properties);
        // open() runs the kernel's own migrations into the pinned schema, then assembles the kernel.
        ForgeKernel kernel = PostgresKernels.open(kernelDataSource, List.of());
        log.info("Forge Platform V2: kernel initialized on schema '{}' (dormant)", schema);
        return kernel;
    }

    @Bean
    public ForgeIdentityBridge forgeIdentityBridge() {
        return new ForgeIdentityBridge();
    }

    @Bean
    public ForgePlatform forgePlatform(ForgeKernel forgeKernel, ForgeIdentityBridge forgeIdentityBridge) {
        return new ForgePlatform(forgeKernel, forgeIdentityBridge);
    }

    private static void createSchemaIfMissing(DataSource dataSource, String schema) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create kernel schema '" + schema + "'", e);
        }
    }

    private static HikariDataSource buildKernelPool(DataSourceProperties dataSourceProperties,
                                                    PlatformProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dataSourceProperties.determineUrl());
        config.setUsername(dataSourceProperties.determineUsername());
        config.setPassword(dataSourceProperties.determinePassword());
        config.setSchema(properties.schema());            // pins the kernel's tables to forge_kernel
        config.setMaximumPoolSize(properties.maxPoolSize());
        config.setMinimumIdle(1);
        config.setPoolName("forge-kernel-pool");
        return new HikariDataSource(config);
    }

    @PreDestroy
    void shutdown() {
        if (kernelDataSource != null && !kernelDataSource.isClosed()) {
            kernelDataSource.close();
            log.info("Forge Platform V2: kernel datasource closed");
        }
    }
}
