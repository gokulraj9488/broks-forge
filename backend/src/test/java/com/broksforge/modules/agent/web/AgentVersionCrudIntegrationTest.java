package com.broksforge.modules.agent.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent version registry: immutable versions, monotonic sequence, single-active-version pointer,
 * activate/rollback (with the rollback-ready gate), duplicate-version rejection, listing and
 * tenant isolation.
 */
@DisplayName("Agent version CRUD")
class AgentVersionCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String agentId;
    private String base;      // agent base
    private String versions;  // versions base

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Ver Org");
        projectId = createProject(owner, orgId, "Ver Project");
        base = projectBase(orgId, projectId) + "/agents";
        agentId = registerAgent(owner, orgId, projectId, "Versioned Agent");
        versions = base + "/" + agentId + "/versions";
    }

    private Map<String, Object> versionBody(String number, boolean activate, boolean rollbackReady) {
        return body(
                "versionNumber", number,
                "model", "gpt-4o-mini",
                "provider", "OPENAI",
                "environment", "PRODUCTION",
                "activate", activate,
                "rollbackReady", rollbackReady);
    }

    @Test
    @DisplayName("registers a version; activate=true makes it the agent's current active version")
    void registersAndActivates() throws Exception {
        JsonNode v = apiPost(owner, versions, versionBody("1.0.0", true, true), 201);
        assertThat(v.get("versionNumber").asText()).isEqualTo("1.0.0");
        assertThat(v.get("sequence").asLong()).isEqualTo(1);
        assertThat(v.get("active").asBoolean()).isTrue();

        JsonNode agent = apiGet(owner, base + "/" + agentId, 200);
        assertThat(agent.get("currentActiveVersionId").asText()).isEqualTo(idOf(v));
    }

    @Test
    @DisplayName("assigns a monotonically increasing sequence")
    void sequenceIncrements() throws Exception {
        apiPost(owner, versions, versionBody("1.0.0", true, true), 201);
        JsonNode v2 = apiPost(owner, versions, versionBody("2.0.0", false, true), 201);
        assertThat(v2.get("sequence").asLong()).isEqualTo(2);
        assertThat(v2.get("active").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("activating a version deactivates the previous active one")
    void activateSwitches() throws Exception {
        String v1 = idOf(apiPost(owner, versions, versionBody("1.0.0", true, true), 201));
        String v2 = idOf(apiPost(owner, versions, versionBody("2.0.0", false, true), 201));

        apiPost(owner, versions + "/" + v2 + "/activate", null, 200);

        JsonNode agent = apiGet(owner, base + "/" + agentId, 200);
        assertThat(agent.get("currentActiveVersionId").asText()).isEqualTo(v2);

        JsonNode list = apiGet(owner, versions, 200);
        for (JsonNode node : list.get("content")) {
            boolean shouldBeActive = node.get("id").asText().equals(v2);
            assertThat(node.get("active").asBoolean()).isEqualTo(shouldBeActive);
        }
        assertThat(v1).isNotEqualTo(v2);
    }

    @Test
    @DisplayName("rejects a duplicate version number with 409")
    void rejectsDuplicateVersion() throws Exception {
        apiPost(owner, versions, versionBody("1.0.0", true, true), 201);
        apiPost(owner, versions, versionBody("1.0.0", false, true), 409);
    }

    @Test
    @DisplayName("rollback requires the target to be rollback-ready (else 400)")
    void rollbackRequiresRollbackReady() throws Exception {
        String notReady = idOf(apiPost(owner, versions, versionBody("1.0.0", false, false), 201));
        apiPost(owner, versions + "/" + notReady + "/rollback", null, 400);
    }

    @Test
    @DisplayName("rollback to a rollback-ready version re-activates it")
    void rollbackReactivates() throws Exception {
        String v1 = idOf(apiPost(owner, versions, versionBody("1.0.0", true, true), 201));
        String v2 = idOf(apiPost(owner, versions, versionBody("2.0.0", true, true), 201));
        // v2 is now active; roll back to v1
        apiPost(owner, versions + "/" + v1 + "/rollback", null, 200);
        assertThat(apiGet(owner, base + "/" + agentId, 200).get("currentActiveVersionId").asText())
                .isEqualTo(v1);
        assertThat(v2).isNotBlank();
    }

    @Test
    @DisplayName("lists versions newest-first")
    void listsNewestFirst() throws Exception {
        apiPost(owner, versions, versionBody("1.0.0", true, true), 201);
        apiPost(owner, versions, versionBody("2.0.0", false, true), 201);
        JsonNode list = apiGet(owner, versions, 200);
        assertThat(list.get("totalElements").asInt()).isEqualTo(2);
        assertThat(list.get("content").get(0).get("sequence").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("rejects a version missing a required field with 400")
    void rejectsMissingModel() throws Exception {
        apiPost(owner, versions,
                body("versionNumber", "1.0.0", "provider", "OPENAI", "environment", "PRODUCTION"), 400);
    }

    @Test
    @DisplayName("versions of another agent are not reachable through this agent (404)")
    void foreignVersionIsolated() throws Exception {
        String otherAgent = registerAgent(owner, orgId, projectId, "Other Agent");
        String otherVersions = base + "/" + otherAgent + "/versions";
        String foreignVersion = idOf(apiPost(owner, otherVersions, versionBody("1.0.0", true, true), 201));
        // address the foreign version through THIS agent -> 404
        apiPost(owner, versions + "/" + foreignVersion + "/activate", null, 404);
    }
}
