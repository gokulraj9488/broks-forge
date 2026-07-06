package com.broksforge.migration;

import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Flyway-managed schema: every migration applied cleanly, and the V30 soft-delete
 * fix is present as a PARTIAL unique index (uniqueness scoped to live rows only).
 */
@DisplayName("Flyway migrations")
class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("all migrations applied successfully (>= 30, none failed)")
    void migrationsApplied() {
        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        Integer succeeded = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(failed).isZero();
        assertThat(succeeded).isGreaterThanOrEqualTo(30);
    }

    @Test
    @DisplayName("agents slug uniqueness is a partial index scoped to non-deleted rows (V30)")
    void slugUniquenessIsPartial() {
        String indexDef = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_agents_project_slug'", String.class);
        assertThat(indexDef).isNotNull();
        assertThat(indexDef.toLowerCase()).contains("unique");
        assertThat(indexDef.toLowerCase()).contains("where (deleted = false)");
    }
}
