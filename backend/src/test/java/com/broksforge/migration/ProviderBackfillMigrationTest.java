package com.broksforge.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V36's automatic backfill (Provider abstraction milestone) only ever runs once, against
 * whatever agents already exist at migration time — the shared Testcontainer every other
 * integration test uses is migrated to the latest version before any test creates an agent, so
 * there is structurally no way to exercise "pre-existing agent gets backfilled" through the
 * normal Spring test harness. This test drives Flyway directly against its own throwaway
 * Postgres: migrate to V35 (providers table exists, agents does not yet have provider_id),
 * insert a raw agent row exactly as a pre-milestone agent would look, migrate to latest, and
 * assert the backfill created a Provider and linked the agent to it — with the agent's own id,
 * endpoint_url and auth_type completely untouched.
 */
@DisplayName("V36 Provider backfill migration")
class ProviderBackfillMigrationTest {

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startContainer() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        postgres.stop();
    }

    @Test
    @DisplayName("backfills a Provider for a pre-existing agent and links it, preserving the agent's id/endpoint/auth")
    void backfillsProviderForPreExistingAgent() throws Exception {
        Flyway toV35 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("35")
                .load();
        toV35.migrate();

        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        Instant now = Instant.now();

        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO users (id, version, created_at, updated_at, email, password_hash, "
                    + "email_verified, status) VALUES ('" + ownerId + "', 0, now(), now(), '"
                    + ownerId + "@example.com', 'x', true, 'ACTIVE')");
            stmt.execute("INSERT INTO organizations (id, version, created_at, updated_at, deleted, name, slug, "
                    + "owner_id) VALUES ('" + orgId + "', 0, now(), now(), false, 'Org', 'org-" + orgId + "', '"
                    + ownerId + "')");
            stmt.execute("INSERT INTO projects (id, version, created_at, updated_at, deleted, organization_id, "
                    + "name, slug, status) VALUES ('" + projectId + "', 0, now(), now(), false, '" + orgId
                    + "', 'Project', 'project-" + projectId + "', 'ACTIVE')");
            stmt.execute("INSERT INTO agents (id, version, created_at, updated_at, deleted, organization_id, "
                    + "project_id, name, slug, owner_id, visibility, framework, language, endpoint_url, auth_type, "
                    + "health_status, status) VALUES ('" + agentId + "', 0, now(), now(), false, '" + orgId + "', '"
                    + projectId + "', 'Pre-Existing Groq Agent', 'pre-existing-groq-agent', '" + ownerId
                    + "', 'PRIVATE', 'CUSTOM_REST', 'PYTHON', "
                    + "'https://api.groq.com/openai/v1/chat/completions', 'NONE', 'UNKNOWN', 'ACTIVE')");
        }

        Flyway toLatest = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        toLatest.migrate();

        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); Statement stmt = conn.createStatement()) {
            ResultSet agentRow = stmt.executeQuery(
                    "SELECT id, endpoint_url, auth_type, provider_id FROM agents WHERE id = '" + agentId + "'");
            assertThat(agentRow.next()).isTrue();
            assertThat(agentRow.getString("id")).isEqualTo(agentId.toString());
            assertThat(agentRow.getString("endpoint_url"))
                    .isEqualTo("https://api.groq.com/openai/v1/chat/completions");
            assertThat(agentRow.getString("auth_type")).isEqualTo("NONE");
            String providerId = agentRow.getString("provider_id");
            assertThat(providerId).isNotNull();

            ResultSet providerRow = stmt.executeQuery(
                    "SELECT type, base_url, project_id, organization_id FROM providers WHERE id = '" + providerId
                            + "'");
            assertThat(providerRow.next()).isTrue();
            assertThat(providerRow.getString("type")).isEqualTo("GROQ");
            assertThat(providerRow.getString("base_url"))
                    .isEqualTo("https://api.groq.com/openai/v1/chat/completions");
            assertThat(providerRow.getString("project_id")).isEqualTo(projectId.toString());
            assertThat(providerRow.getString("organization_id")).isEqualTo(orgId.toString());
        }
    }
}
