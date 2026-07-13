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
 * Agent CRUD & lifecycle beyond registration (which {@link AgentRegistrationIntegrationTest} covers):
 * read with readiness, partial update + endpoint validation, archive/unarchive, the archived-mutation
 * guard, soft-delete, list filtering/pagination, tenant isolation and permission checks.
 */
@DisplayName("Agent CRUD & lifecycle")
class AgentCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Agent Org");
        projectId = createProject(owner, orgId, "Agent Project");
        base = projectBase(orgId, projectId) + "/agents";
    }

    @Test
    @DisplayName("gets an agent with readiness (NONE auth => credentialConfigured true, health UNKNOWN)")
    void getsWithReadiness() throws Exception {
        String id = registerAgent(owner, orgId, projectId, "Readable Agent");
        JsonNode agent = apiGet(owner, base + "/" + id, 200);
        assertThat(agent.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(agent.get("healthStatus").asText()).isEqualTo("UNKNOWN");
        assertThat(agent.get("authType").asText()).isEqualTo("NONE");
        assertThat(agent.get("credentialConfigured").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("updates mutable fields")
    void updatesFields() throws Exception {
        String id = registerAgent(owner, orgId, projectId, "Before");
        JsonNode updated = apiPatch(owner, base + "/" + id,
                Map.of("name", "After", "description", "desc", "visibility", "ORGANIZATION"), 200);
        assertThat(updated.get("name").asText()).isEqualTo("After");
        assertThat(updated.get("visibility").asText()).isEqualTo("ORGANIZATION");
    }

    @Test
    @DisplayName("rejects an invalid endpoint URL on update with 400")
    void rejectsInvalidEndpoint() throws Exception {
        String id = registerAgent(owner, orgId, projectId, "Agent");
        apiPatch(owner, base + "/" + id, Map.of("endpointUrl", "ftp://not-allowed"), 400);
    }

    @Test
    @DisplayName("archives and unarchives")
    void archivesAndUnarchives() throws Exception {
        String id = registerAgent(owner, orgId, projectId, "Lifecycle Agent");
        JsonNode archived = apiPost(owner, base + "/" + id + "/archive", null, 200);
        assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
        JsonNode active = apiPost(owner, base + "/" + id + "/unarchive", null, 200);
        assertThat(active.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("blocks mutating an archived agent with 409")
    void archivedCannotBeUpdated() throws Exception {
        String id = registerAgent(owner, orgId, projectId, "Archived Agent");
        apiPost(owner, base + "/" + id + "/archive", null, 200);
        apiPatch(owner, base + "/" + id, Map.of("name", "Nope"), 409);
    }

    @Test
    @DisplayName("soft-deletes an agent (ADMIN+), after which it is not found")
    void softDeletes() throws Exception {
        String id = registerAgent(owner, orgId, projectId, "Doomed Agent");
        apiDelete(owner, base + "/" + id, 204);
        apiGet(owner, base + "/" + id, 404);
    }

    @Test
    @DisplayName("a MEMBER cannot delete an agent (delete requires ADMIN)")
    void memberCannotDelete() throws Exception {
        String id = registerAgent(owner, orgId, projectId, "Guarded Agent");
        String member = addMember(owner, orgId, "MEMBER");
        apiDelete(member, base + "/" + id, 403);
    }

    @Test
    @DisplayName("lists, searches and filters agents")
    void listsAndFilters() throws Exception {
        registerAgent(owner, orgId, projectId, "Alpha Agent");
        registerAgent(owner, orgId, projectId, "Beta Agent");

        JsonNode all = apiGet(owner, base, 200);
        assertThat(all.get("totalElements").asInt()).isEqualTo(2);

        assertThat(apiGet(owner, base + "?q=Alpha", 200).get("totalElements").asInt()).isEqualTo(1);
        assertThat(apiGet(owner, base + "?framework=CUSTOM_REST", 200).get("totalElements").asInt()).isEqualTo(2);
        assertThat(apiGet(owner, base + "?framework=SPRING_AI", 200).get("totalElements").asInt()).isZero();
        assertThat(apiGet(owner, base + "?status=ARCHIVED", 200).get("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("returns 404 for an agent belonging to another project (tenant isolation)")
    void foreignAgentIsNotFound() throws Exception {
        String otherProject = createProject(owner, orgId, "Other Project");
        String foreign = registerAgent(owner, orgId, otherProject, "Theirs");
        apiGet(owner, base + "/" + foreign, 404);
        apiGet(owner, base + "/" + UUID.randomUUID(), 404);
    }

    @Test
    @DisplayName("a non-member cannot list agents (403)")
    void nonMemberForbidden() throws Exception {
        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        apiGet(stranger, base, 403);
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuth() throws Exception {
        apiGet(null, base, 401);
    }
}
