package com.broksforge.modules.agent.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent health probing and history. The agent points at {@code 127.0.0.1:9} so a probe fails fast
 * (connection refused) and the result is deterministic without touching the network.
 */
@DisplayName("Agent health")
class AgentHealthIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String agentBase;
    private String agentId;
    private String hbase;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Health Org");
        projectId = createProject(owner, orgId, "Health Project");
        agentBase = projectBase(orgId, projectId) + "/agents";
        agentId = idOf(apiPost(owner, agentBase, Map.of(
                "name", "Probed Agent",
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "http://127.0.0.1:9/",
                "authType", "NONE"), 201));
        hbase = agentBase + "/" + agentId;
    }

    @Test
    @DisplayName("reports an empty health summary before any check")
    void emptySummary() throws Exception {
        JsonNode summary = apiGet(owner, hbase + "/health", 200);
        assertThat(summary.get("currentStatus").asText()).isEqualTo("UNKNOWN");
        assertThat(summary.get("totalChecks").asLong()).isZero();
        // availabilityPercent is null with no checks; null fields are omitted, so the node is absent
        assertThat(summary.get("availabilityPercent")).isNull();
        assertThat(summary.get("recent")).isEmpty();
    }

    @Test
    @DisplayName("running a check records a result and updates the summary")
    void runCheckRecordsResult() throws Exception {
        JsonNode check = apiPost(owner, hbase + "/health-check", null, 200);
        assertThat(check.get("success").asBoolean()).isFalse();          // connection refused
        assertThat(check.get("status").asText()).isNotEqualTo("HEALTHY");

        JsonNode summary = apiGet(owner, hbase + "/health", 200);
        assertThat(summary.get("totalChecks").asLong()).isEqualTo(1);
        assertThat(summary.get("currentStatus").asText()).isNotEqualTo("HEALTHY");
    }

    @Test
    @DisplayName("paginates health-check history")
    void paginatesHistory() throws Exception {
        apiPost(owner, hbase + "/health-check", null, 200);
        apiPost(owner, hbase + "/health-check", null, 200);
        JsonNode history = apiGet(owner, hbase + "/health/history", 200);
        assertThat(history.get("totalElements").asInt()).isEqualTo(2);
        assertThat(history.get("content")).hasSize(2);
    }

    @Test
    @DisplayName("returns 404 for the health of an unknown agent")
    void unknownAgentIsNotFound() throws Exception {
        apiGet(owner, agentBase + "/" + UUID.randomUUID() + "/health", 404);
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuth() throws Exception {
        apiGet(null, hbase + "/health", 401);
    }
}
