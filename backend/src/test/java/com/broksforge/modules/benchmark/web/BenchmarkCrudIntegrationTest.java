package com.broksforge.modules.benchmark.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmark CRUD and entry management: create (with/without entries), add/remove entries referencing
 * real evaluation jobs, duplicate-entry rejection, leaderboard, soft-delete (benchmark) vs hard-delete
 * (entry), validation, tenant isolation and permissions.
 */
@DisplayName("Benchmark CRUD")
class BenchmarkCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;
    private String jobId;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Bench Org");
        projectId = createProject(owner, orgId, "Bench Project");
        base = projectBase(orgId, projectId) + "/benchmarks";
        String agentId = registerAgent(owner, orgId, projectId, "Bench Agent");
        String datasetId = createDatasetWithItems(owner, orgId, projectId, "Bench Dataset");
        jobId = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Bench Job");
    }

    @Test
    @DisplayName("creates a benchmark with the default metric key and no entries")
    void createsBenchmark() throws Exception {
        JsonNode b = apiPost(owner, base, Map.of("name", "Q3 Bench", "type", "AGENT_VS_AGENT"), 201);
        assertThat(b.get("entryCount").asInt()).isZero();
        assertThat(b.get("metricKey").asText()).isEqualTo("passRate");
        assertThat(b.get("type").asText()).isEqualTo("AGENT_VS_AGENT");
    }

    @Test
    @DisplayName("creates a benchmark with entries inline")
    void createsWithEntries() throws Exception {
        JsonNode b = apiPost(owner, base, body(
                "name", "With Entries", "type", "AGENT_VS_AGENT",
                "entries", List.of(Map.of("evaluationJobId", jobId))), 201);
        assertThat(b.get("entryCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects a blank name (400) and a missing type (400)")
    void rejectsInvalid() throws Exception {
        apiPost(owner, base, Map.of("name", "", "type", "AGENT_VS_AGENT"), 400);
        apiPost(owner, base, Map.of("name", "No Type"), 400);
    }

    @Test
    @DisplayName("adds an entry (200), rejects a duplicate job (409) and a foreign job (404)")
    void addsEntry() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Addable", "type", "AGENT_VS_AGENT"), 201));
        apiPost(owner, base + "/" + id + "/entries", Map.of("evaluationJobId", jobId), 200);
        assertThat(apiGet(owner, base + "/" + id, 200).get("entryCount").asInt()).isEqualTo(1);

        apiPost(owner, base + "/" + id + "/entries", Map.of("evaluationJobId", jobId), 409);
        apiPost(owner, base + "/" + id + "/entries", Map.of("evaluationJobId", UUID.randomUUID().toString()), 404);
    }

    @Test
    @DisplayName("removes an entry (hard delete, 204)")
    void removesEntry() throws Exception {
        String id = idOf(apiPost(owner, base, body(
                "name", "Removable", "type", "AGENT_VS_AGENT",
                "entries", List.of(Map.of("evaluationJobId", jobId))), 201));
        String entryId = apiGet(owner, base + "/" + id, 200).get("entries").get(0).get("id").asText();
        apiDelete(owner, base + "/" + id + "/entries/" + entryId, 204);
        assertThat(apiGet(owner, base + "/" + id, 200).get("entryCount").asInt()).isZero();
    }

    @Test
    @DisplayName("computes a leaderboard and lists benchmarks")
    void leaderboardAndList() throws Exception {
        String id = idOf(apiPost(owner, base, body(
                "name", "Ranked", "type", "AGENT_VS_AGENT",
                "entries", List.of(Map.of("evaluationJobId", jobId))), 201));
        apiGet(owner, base + "/" + id + "/leaderboard", 200);
        assertThat(apiGet(owner, base, 200).get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("soft-deletes (ADMIN); a MEMBER cannot delete")
    void softDeleteAndPermissions() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Doomed Bench", "type", "AGENT_VS_AGENT"), 201));
        String member = addMember(owner, orgId, "MEMBER");
        apiDelete(member, base + "/" + id, 403);
        apiDelete(owner, base + "/" + id, 204);
        apiGet(owner, base + "/" + id, 404);
    }

    @Test
    @DisplayName("enforces tenant isolation and auth")
    void isolationAndAuth() throws Exception {
        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        apiGet(stranger, base, 403);
        apiGet(owner, base + "/" + UUID.randomUUID(), 404);
        apiGet(null, base, 401);
    }
}
