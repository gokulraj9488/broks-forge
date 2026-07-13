package com.broksforge.modules.apikey.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for the {@code apikey} module: create (plaintext shown exactly once), list (never leaks a
 * secret), revoke, expiry validation, and access control. API keys are <b>revoked</b> (a flag), not
 * soft-deleted, and have no slug — so the matrix differs from the slug-bearing aggregates.
 */
@DisplayName("API key CRUD")
class ApiKeyCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Key Org");
        projectId = createProject(owner, orgId, "Key Project");
        base = projectBase(orgId, projectId) + "/api-keys";
    }

    @Test
    @DisplayName("creates a key and returns the plaintext exactly once")
    void createsKey() throws Exception {
        JsonNode created = apiPost(owner, base, Map.of("name", "CI key"), 201);
        assertThat(created.get("plaintextKey").asText()).isNotBlank();
        JsonNode meta = created.get("apiKey");
        assertThat(meta.get("name").asText()).isEqualTo("CI key");
        assertThat(meta.get("keyPrefix").asText()).isNotBlank();
        assertThat(meta.get("revoked").asBoolean()).isFalse();
        assertThat(meta.has("secret")).isFalse();
    }

    @Test
    @DisplayName("supports an expiry window")
    void supportsExpiry() throws Exception {
        JsonNode created = apiPost(owner, base, Map.of("name", "Expiring", "expiresInDays", 30), 201);
        assertThat(created.get("apiKey").get("expiresAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("rejects a non-positive expiry with 400")
    void rejectsZeroExpiry() throws Exception {
        apiPost(owner, base, Map.of("name", "Bad", "expiresInDays", 0), 400);
    }

    @Test
    @DisplayName("rejects a blank name with 400")
    void rejectsBlankName() throws Exception {
        apiPost(owner, base, Map.of("name", ""), 400);
    }

    @Test
    @DisplayName("lists key metadata without ever leaking the secret")
    void listsWithoutSecret() throws Exception {
        String plaintext = apiPost(owner, base, Map.of("name", "Listed"), 201).get("plaintextKey").asText();
        String listRaw = call("GET", owner, base, null).andReturn().getResponse().getContentAsString();
        assertThat(listRaw).doesNotContain(plaintext);

        JsonNode page = objectMapper.readTree(listRaw);
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("revokes a key (retained with revoked=true), and revoking an unknown id is 404")
    void revokesKey() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Revoke me"), 201).get("apiKey"));
        apiDelete(owner, base + "/" + id, 204);

        JsonNode page = apiGet(owner, base, 200);
        assertThat(page.get("content").get(0).get("revoked").asBoolean()).isTrue();

        apiDelete(owner, base + "/" + UUID.randomUUID(), 404);
    }

    @Test
    @DisplayName("a MEMBER cannot create a key (requires ADMIN+)")
    void memberCannotCreate() throws Exception {
        String member = addMember(owner, orgId, "MEMBER");
        apiPost(member, base, Map.of("name", "Nope"), 403);
    }

    @Test
    @DisplayName("a non-member cannot list keys (403)")
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
