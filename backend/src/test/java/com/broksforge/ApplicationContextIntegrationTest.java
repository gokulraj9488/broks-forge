package com.broksforge;

import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The canonical Spring Boot smoke test: if the full application context boots against a real
 * PostgreSQL — running every Flyway migration and then Hibernate's {@code ddl-auto=validate} —
 * then the entity model, migrations, bean wiring and configuration are mutually consistent.
 */
@DisplayName("Application context")
class ApplicationContextIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("boots the full context (Flyway migrate + schema validate + all beans)")
    void contextLoads() {
        // Intentionally empty — a failure here means the context could not start.
    }
}
