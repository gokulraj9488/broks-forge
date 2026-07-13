package com.broksforge.modules.project.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full CRUD + lifecycle contract for the {@code project} module: create/read/update/soft-delete,
 * slug generation & reuse, duplicate handling, validation, pagination, tenant isolation (foreign
 * project id -> 404, non-member -> 403) and permission checks (create = MEMBER, delete = ADMIN).
 */
@DisplayName("Project CRUD")
class ProjectCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Host Org");
        base = "/api/v1/organizations/" + orgId + "/projects";
    }

    @Test
    @DisplayName("creates a project, generating a slug and linking it to the org")
    void createsProject() throws Exception {
        JsonNode project = apiPost(owner, base, Map.of("name", "Support Agent"), 201);
        assertThat(project.get("name").asText()).isEqualTo("Support Agent");
        assertThat(project.get("slug").asText()).isEqualTo("support-agent");
        assertThat(project.get("organizationId").asText()).isEqualTo(orgId);
        assertThat(project.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("auto-suffixes the slug for a duplicate name within the org")
    void autoSuffixesDuplicateName() throws Exception {
        apiPost(owner, base, Map.of("name", "Support Agent"), 201);
        JsonNode second = apiPost(owner, base, Map.of("name", "Support Agent"), 201);
        assertThat(second.get("slug").asText()).isEqualTo("support-agent-2");
    }

    @Test
    @DisplayName("rejects a duplicate explicit slug within the org with 409")
    void rejectsDuplicateExplicitSlug() throws Exception {
        apiPost(owner, base, Map.of("name", "First", "slug", "shared"), 201);
        apiPost(owner, base, Map.of("name", "Second", "slug", "shared"), 409);
    }

    @Test
    @DisplayName("allows the same slug in a different org (slug is unique per org, not global)")
    void sameSlugDifferentOrg() throws Exception {
        apiPost(owner, base, Map.of("name", "Proj", "slug", "shared"), 201);
        String otherOrg = createOrg(owner, "Other Org");
        apiPost(owner, "/api/v1/organizations/" + otherOrg + "/projects",
                Map.of("name", "Proj", "slug", "shared"), 201);
    }

    @Test
    @DisplayName("rejects a blank name with 400")
    void rejectsBlankName() throws Exception {
        apiPost(owner, base, Map.of("name", ""), 400);
    }

    @Test
    @DisplayName("gets and lists projects scoped to the org")
    void getsAndLists() throws Exception {
        String id = createProject(owner, orgId, "Listed");
        assertThat(apiGet(owner, base + "/" + id, 200).get("id").asText()).isEqualTo(id);

        JsonNode page = apiGet(owner, base, 200);
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        assertThat(page.get("content")).hasSize(1);
    }

    @Test
    @DisplayName("updates a project and archives it via status")
    void updatesAndArchives() throws Exception {
        String id = createProject(owner, orgId, "Before");
        JsonNode updated = apiPatch(owner, base + "/" + id,
                Map.of("name", "After", "description", "desc"), 200);
        assertThat(updated.get("name").asText()).isEqualTo("After");

        JsonNode archived = apiPatch(owner, base + "/" + id, Map.of("status", "ARCHIVED"), 200);
        assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("soft-deletes a project and frees its slug for reuse")
    void softDeletesAndFreesSlug() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Temp", "slug", "temp-slug"), 201));
        apiDelete(owner, base + "/" + id, 204);
        apiGet(owner, base + "/" + id, 404);

        JsonNode recreated = apiPost(owner, base, Map.of("name", "Temp", "slug", "temp-slug"), 201);
        assertThat(recreated.get("slug").asText()).isEqualTo("temp-slug");
    }

    @Test
    @DisplayName("returns 404 for a project id that belongs to another org (tenant isolation / IDOR)")
    void foreignProjectIsNotFound() throws Exception {
        String otherOrg = createOrg(owner, "Neighbour Org");
        String foreignProject = createProject(owner, otherOrg, "Theirs");
        // Address the foreign project through THIS org's path -> resolved by (id, orgId) tuple -> 404.
        apiGet(owner, base + "/" + foreignProject, 404);
    }

    @Test
    @DisplayName("returns 404 for a random project id")
    void unknownProjectIsNotFound() throws Exception {
        apiGet(owner, base + "/" + UUID.randomUUID(), 404);
    }

    @Test
    @DisplayName("a non-member of the org cannot list its projects (403)")
    void nonMemberForbidden() throws Exception {
        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        apiGet(stranger, base, 403);
    }

    @Test
    @DisplayName("a MEMBER can create a project but cannot delete it (delete requires ADMIN)")
    void memberCreateButNotDelete() throws Exception {
        String member = addMember(owner, orgId, "MEMBER");
        String id = idOf(apiPost(member, base, Map.of("name", "Member Project"), 201));
        apiDelete(member, base + "/" + id, 403);
        // owner (ADMIN+) can
        apiDelete(owner, base + "/" + id, 204);
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuth() throws Exception {
        apiGet(null, base, 401);
    }
}
