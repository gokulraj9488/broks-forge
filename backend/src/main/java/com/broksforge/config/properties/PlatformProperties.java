package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the Platform V2 wiring (bound from {@code broksforge.platform.v2.*}).
 *
 * <p>P0 (foundation phase) only. When {@code enabled} is false the platform beans are not created and the
 * application runs exactly as before — this is the rollback switch. The kernel's tables live in an isolated
 * {@code schema} so they never interact with the application's Flyway/JPA schema. {@code probeOrgId} is a
 * reserved organization used solely for the startup health check; it never holds tenant data.
 *
 * @param enabled          master switch; false = platform not initialized (dormant/off)
 * @param schema           Postgres schema that isolates the kernel's append log (default {@code forge_kernel})
 * @param probeOrgId       reserved org id used only for startup health verification
 * @param maxPoolSize      connection pool size for the dedicated (small) kernel datasource
 * @param backfillOnStartup when true, run the idempotent Knowledge projection backfill once at startup
 *                          (default false → startup behavior unchanged; backfill is opt-in and reversible)
 */
@ConfigurationProperties(prefix = "broksforge.platform.v2")
public record PlatformProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("forge_kernel") String schema,
        @DefaultValue("00000000-0000-0000-0000-000000000000") String probeOrgId,
        @DefaultValue("4") int maxPoolSize,
        @DefaultValue("false") boolean backfillOnStartup) {
}
