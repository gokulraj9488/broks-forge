package com.broksforge.modules.prompt.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt CRUD plus the immutable, versioned template model (ADR 0008): create/read/update/soft-delete,
 * slug rules, {@code {{variable}}} extraction, first-version auto-activation, activate/rollback,
 * version comparison, tenant isolation and permissions.
 */
@DisplayName("Prompt CRUD & versioning")
class PromptCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Prompt Org");
        projectId = createProject(owner, orgId, "Prompt Project");
        base = projectBase(orgId, projectId) + "/prompts";
    }

    @Test
    @DisplayName("creates a prompt with a generated slug and no active version yet")
    void createsPrompt() throws Exception {
        JsonNode p = apiPost(owner, base, Map.of("name", "Support Reply"), 201);
        assertThat(p.get("slug").asText()).isEqualTo("support-reply");
        assertThat(p.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(p.get("latestVersionNumber").asInt()).isZero();
        // no active version yet; null fields are omitted from the response, so the node is absent
        assertThat(p.get("currentActiveVersionId")).isNull();
    }

    @Test
    @DisplayName("rejects a duplicate explicit slug (409) and a blank name (400)")
    void rejectsDuplicateSlugAndBlankName() throws Exception {
        apiPost(owner, base, Map.of("name", "First", "slug", "clash"), 201);
        apiPost(owner, base, Map.of("name", "Second", "slug", "clash"), 409);
        apiPost(owner, base, Map.of("name", ""), 400);
    }

    @Test
    @DisplayName("the first version auto-activates and extracts distinct template variables")
    void firstVersionAutoActivates() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Templated"), 201));
        JsonNode v = apiPost(owner, base + "/" + id + "/versions",
                Map.of("template", "Answer {{question}} for {{user}} ({{question}})"), 201);
        assertThat(v.get("versionNumber").asInt()).isEqualTo(1);
        assertThat(v.get("active").asBoolean()).isTrue();
        assertThat(v.get("variables").toString()).contains("question").contains("user");
        // distinct extraction: {{question}} appears twice but is listed once
        assertThat(v.get("variables")).hasSize(2);

        assertThat(apiGet(owner, base + "/" + id, 200).get("currentActiveVersionId").asText())
                .isEqualTo(idOf(v));
    }

    @Test
    @DisplayName("activate and rollback move the active-version pointer")
    void activateAndRollback() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Rollable"), 201));
        String v1 = idOf(apiPost(owner, base + "/" + id + "/versions",
                Map.of("template", "V1 {{a}}", "activate", true), 201));
        String v2 = idOf(apiPost(owner, base + "/" + id + "/versions",
                Map.of("template", "V2 {{a}} {{b}}", "activate", false), 201));

        // v1 still active until we activate v2
        assertThat(apiGet(owner, base + "/" + id, 200).get("currentActiveVersionId").asText()).isEqualTo(v1);
        apiPost(owner, base + "/" + id + "/versions/" + v2 + "/activate", null, 200);
        assertThat(apiGet(owner, base + "/" + id, 200).get("currentActiveVersionId").asText()).isEqualTo(v2);
        apiPost(owner, base + "/" + id + "/versions/" + v1 + "/rollback", null, 200);
        assertThat(apiGet(owner, base + "/" + id, 200).get("currentActiveVersionId").asText()).isEqualTo(v1);
    }

    @Test
    @DisplayName("compares two versions, diffing their variables")
    void comparesVersions() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Comparable"), 201));
        String v1 = idOf(apiPost(owner, base + "/" + id + "/versions",
                Map.of("template", "Hello {{a}}", "activate", true), 201));
        String v2 = idOf(apiPost(owner, base + "/" + id + "/versions",
                Map.of("template", "Hello {{a}} and {{b}}", "activate", false), 201));

        JsonNode cmp = apiGet(owner, base + "/" + id + "/compare?from=" + v1 + "&to=" + v2, 200);
        assertThat(cmp.get("addedVariables").toString()).contains("b");
        assertThat(cmp.get("commonVariables").toString()).contains("a");
        assertThat(cmp.get("identicalTemplate").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("rejects a blank template with 400")
    void rejectsBlankTemplate() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Bad Version"), 201));
        apiPost(owner, base + "/" + id + "/versions", Map.of("template", ""), 400);
    }

    @Test
    @DisplayName("archived prompt rejects new versions (409); update + soft-delete respect permissions")
    void archiveGuardAndDelete() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Guarded", "slug", "guarded-pr"), 201));
        apiPost(owner, base + "/" + id + "/archive", null, 200);
        apiPost(owner, base + "/" + id + "/versions", Map.of("template", "nope {{x}}"), 409);
        apiPost(owner, base + "/" + id + "/unarchive", null, 200);

        String member = addMember(owner, orgId, "MEMBER");
        apiDelete(member, base + "/" + id, 403);
        apiDelete(owner, base + "/" + id, 204);

        // slug reuse after soft-delete
        JsonNode recreated = apiPost(owner, base, Map.of("name", "Guarded 2", "slug", "guarded-pr"), 201);
        assertThat(recreated.get("slug").asText()).isEqualTo("guarded-pr");
    }

    @Test
    @DisplayName("enforces tenant isolation and auth")
    void isolationAndAuth() throws Exception {
        String otherProject = createProject(owner, orgId, "Other Prompt Project");
        String foreign = createPromptWithVersion(owner, orgId, otherProject, "Theirs");
        apiGet(owner, base + "/" + foreign, 404);
        apiGet(owner, base + "/" + UUID.randomUUID(), 404);

        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        apiGet(stranger, base, 403);
        apiGet(null, base, 401);
    }
}
