package com.broksforge;

import com.broksforge.config.StartupDiagnosticsLogger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Brok's Forge API.
 *
 * <p>Brok's Forge is a modular monolith. Each feature lives under
 * {@code com.broksforge.modules} and is wired through clearly defined service
 * boundaries so that individual modules can later be extracted into independent
 * microservices without rewriting their internals.</p>
 *
 * <p>Redis is used purely as a template-backed cache / token store (see
 * {@code RedisConfig}); there are no Spring Data Redis repositories. Excluding
 * {@link RedisRepositoriesAutoConfiguration} stops the Redis repository scanner
 * from inspecting the JPA repositories at startup — which otherwise logs a
 * "Could not safely identify store assignment" line for every JPA repository —
 * without affecting the Redis connection factory or {@code RedisTemplate}.</p>
 */
@SpringBootApplication(exclude = RedisRepositoriesAutoConfiguration.class)
@ConfigurationPropertiesScan
public class BroksForgeApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BroksForgeApplication.class);
        // Registered as a listener (not a @Component) so it fires on
        // ApplicationEnvironmentPreparedEvent — before the DataSource, Flyway, or
        // EntityManagerFactory beans are created. See StartupDiagnosticsLogger's javadoc.
        app.addListeners(new StartupDiagnosticsLogger());
        app.run(args);
    }
}
