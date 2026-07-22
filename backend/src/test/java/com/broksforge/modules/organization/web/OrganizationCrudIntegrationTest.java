package com.broksforge.modules.organization.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full CRUD + lifecycle contract for the {@code organization} module: create/read/update/soft-delete,
 * slug generation and reuse, duplicate handling, validation, pagination, membership management,
 * tenant isolation and permission checks — all against a real PostgreSQL via {@link AbstractIntegrationTest}.
 */
@DisplayName("Organization CRUD")
class OrganizationCrudIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/organizations";
    private String owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
    }

    /**
     * A short lowercase-hex token. Organization slugs are <b>globally</b> unique (schema constraint
     * {@code uq_organizations_slug}), and the integration DB is shared across the whole run, so any
     * test asserting an exact slug must use a per-test-unique base to avoid auto-suffix collisions
     * with sibling tests. (Project/agent/dataset slugs are per-tenant, so their tests don't need this.)
     */
    private static String rand() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates an org, generating a slug from the name and making the caller OWNER")
        void createsOrg() throws Exception {
            String tag = rand();
            JsonNode org = apiPost(owner, BASE, Map.of("name", "Acme " + tag), 201);
            assertThat(org.get("name").asText()).isEqualTo("Acme " + tag);
            assertThat(org.get("slug").asText()).isEqualTo("acme-" + tag);
            assertThat(org.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(org.get("currentUserRole").asText()).isEqualTo("OWNER");
            assertThat(org.get("memberCount").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("honours an explicit valid slug")
        void honoursExplicitSlug() throws Exception {
            String slug = "custom-" + rand();
            JsonNode org = apiPost(owner, BASE, Map.of("name", "Acme AI", "slug", slug), 201);
            assertThat(org.get("slug").asText()).isEqualTo(slug);
        }

        @Test
        @DisplayName("auto-suffixes the slug when two orgs share a name")
        void autoSuffixesDuplicateName() throws Exception {
            String tag = rand();
            String name = "Dup " + tag;
            JsonNode first = apiPost(owner, BASE, Map.of("name", name), 201);
            assertThat(first.get("slug").asText()).isEqualTo("dup-" + tag);
            JsonNode second = apiPost(owner, BASE, Map.of("name", name), 201);
            assertThat(second.get("slug").asText()).isEqualTo("dup-" + tag + "-2");
        }

        @Test
        @DisplayName("rejects a duplicate explicit slug with 409")
        void rejectsDuplicateExplicitSlug() throws Exception {
            String slug = "taken-" + rand();
            apiPost(owner, BASE, Map.of("name", "First", "slug", slug), 201);
            JsonNode err = apiPost(owner, BASE, Map.of("name", "Second", "slug", slug), 409);
            assertThat(err.get("code").asText()).isEqualTo("SLUG_ALREADY_EXISTS");
        }

        @Test
        @DisplayName("rejects a blank name with 400")
        void rejectsBlankName() throws Exception {
            apiPost(owner, BASE, Map.of("name", ""), 400);
        }

        @Test
        @DisplayName("rejects a too-short name with 400")
        void rejectsShortName() throws Exception {
            apiPost(owner, BASE, Map.of("name", "A"), 400);
        }

        @Test
        @DisplayName("rejects a malformed slug with 400")
        void rejectsMalformedSlug() throws Exception {
            apiPost(owner, BASE, Map.of("name", "Valid Name", "slug", "Not_A Slug"), 400);
        }
    }

    @Nested
    @DisplayName("read & list")
    class Read {

        @Test
        @DisplayName("gets an org by id")
        void getsById() throws Exception {
            String id = createOrg(owner, "Readable Org");
            JsonNode org = apiGet(owner, BASE + "/" + id, 200);
            assertThat(org.get("id").asText()).isEqualTo(id);
            assertThat(org.get("name").asText()).isEqualTo("Readable Org");
        }

        @Test
        @DisplayName("lists only the orgs the caller belongs to (empty for a fresh user)")
        void listsOnlyOwn() throws Exception {
            JsonNode empty = apiGet(owner, BASE, 200);
            assertThat(empty.get("totalElements").asInt()).isZero();
            assertThat(empty.get("content")).isEmpty();

            createOrg(owner, "One");
            createOrg(owner, "Two");
            JsonNode page = apiGet(owner, BASE, 200);
            assertThat(page.get("totalElements").asInt()).isEqualTo(2);
            assertThat(page.get("content")).hasSize(2);
            assertThat(page.get("first").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("paginates the org list")
        void paginates() throws Exception {
            for (int i = 0; i < 3; i++) {
                createOrg(owner, "Org " + i);
            }
            JsonNode page0 = apiGet(owner, BASE + "?page=0&size=2", 200);
            assertThat(page0.get("content")).hasSize(2);
            assertThat(page0.get("totalElements").asInt()).isEqualTo(3);
            assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
            assertThat(page0.get("hasNext").asBoolean()).isTrue();

            JsonNode page1 = apiGet(owner, BASE + "?page=1&size=2", 200);
            assertThat(page1.get("content")).hasSize(1);
            assertThat(page1.get("last").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("returns 403 for an unknown org the caller is not a member of (deny-by-default)")
        void unknownIsNotFoundOrForbidden() throws Exception {
            // A random id: caller is not a member, so membership check fails first -> 403 (deny-by-default).
            apiGet(owner, BASE + "/" + java.util.UUID.randomUUID(), 403);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("updates name and description")
        void updatesFields() throws Exception {
            String id = createOrg(owner, "Before");
            JsonNode updated = apiPatch(owner, BASE + "/" + id,
                    Map.of("name", "After", "description", "Now described"), 200);
            assertThat(updated.get("name").asText()).isEqualTo("After");
            assertThat(updated.get("description").asText()).isEqualTo("Now described");
            // slug is immutable on update
            assertThat(updated.get("slug").asText()).isEqualTo("before");
        }

        @Test
        @DisplayName("archives via status")
        void archives() throws Exception {
            String id = createOrg(owner, "To Archive");
            JsonNode updated = apiPatch(owner, BASE + "/" + id, Map.of("status", "ARCHIVED"), 200);
            assertThat(updated.get("status").asText()).isEqualTo("ARCHIVED");
        }

        @Test
        @DisplayName("leaves unspecified fields unchanged")
        void partialUpdate() throws Exception {
            String id = createOrg(owner, "Keep Name");
            apiPatch(owner, BASE + "/" + id, Map.of("description", "only desc"), 200);
            JsonNode org = apiGet(owner, BASE + "/" + id, 200);
            assertThat(org.get("name").asText()).isEqualTo("Keep Name");
            assertThat(org.get("description").asText()).isEqualTo("only desc");
        }
    }

    @Nested
    @DisplayName("soft delete")
    class Delete {

        @Test
        @DisplayName("soft-deletes (OWNER), after which the org is gone and its slug frees up")
        void softDeletesAndFreesSlug() throws Exception {
            String slug = "recycle-" + rand();
            String id = idOf(apiPost(owner, BASE, Map.of("name", "Recycle Me", "slug", slug), 201));
            apiDelete(owner, BASE + "/" + id, 204);

            // gone from reads
            apiGet(owner, BASE + "/" + id, 404);
            assertThat(apiGet(owner, BASE, 200).get("totalElements").asInt()).isZero();

            // slug reusable after soft-delete (partial unique index excludes deleted rows)
            JsonNode recreated = apiPost(owner, BASE, Map.of("name", "Recycle Again", "slug", slug), 201);
            assertThat(recreated.get("slug").asText()).isEqualTo(slug);
        }

        @Test
        @DisplayName("does not expose any restore endpoint (soft delete is one-way via the API)")
        void noRestoreEndpoint() throws Exception {
            String id = createOrg(owner, "Gone");
            apiDelete(owner, BASE + "/" + id, 204);
            // there is no POST .../restore or .../unarchive on organizations
            call("POST", owner, BASE + "/" + id + "/restore", null)
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("members")
    class Members {

        @Test
        @DisplayName("adds an existing user as a member, then lists both")
        void addsAndListsMembers() throws Exception {
            String id = createOrg(owner, "Team Org");
            String memberEmail = uniqueEmail();
            registerAndGetToken(memberEmail, "StrongPass!2026");
            apiPost(owner, BASE + "/" + id + "/members",
                    Map.of("email", memberEmail, "role", "MEMBER"), 201);

            JsonNode members = apiGet(owner, BASE + "/" + id + "/members", 200);
            assertThat(members.get("totalElements").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("rejects adding an unknown email with 404")
        void rejectsUnknownEmail() throws Exception {
            String id = createOrg(owner, "Team Org");
            apiPost(owner, BASE + "/" + id + "/members",
                    Map.of("email", "nobody-" + java.util.UUID.randomUUID() + "@example.com", "role", "MEMBER"), 404);
        }

        @Test
        @DisplayName("rejects granting OWNER through add-member with 400")
        void rejectsOwnerGrant() throws Exception {
            String id = createOrg(owner, "Team Org");
            String memberEmail = uniqueEmail();
            registerAndGetToken(memberEmail, "StrongPass!2026");
            apiPost(owner, BASE + "/" + id + "/members",
                    Map.of("email", memberEmail, "role", "OWNER"), 400);
        }

        @Test
        @DisplayName("rejects adding the same member twice with 409")
        void rejectsDuplicateMember() throws Exception {
            String id = createOrg(owner, "Team Org");
            String memberEmail = uniqueEmail();
            registerAndGetToken(memberEmail, "StrongPass!2026");
            apiPost(owner, BASE + "/" + id + "/members", Map.of("email", memberEmail, "role", "MEMBER"), 201);
            apiPost(owner, BASE + "/" + id + "/members", Map.of("email", memberEmail, "role", "MEMBER"), 409);
        }

        @Test
        @DisplayName("blocks demoting the last owner with 409")
        void blocksDemotingLastOwner() throws Exception {
            String id = createOrg(owner, "Solo Org");
            JsonNode me = apiGet(owner, "/api/v1/users/me", 200);
            String ownerUserId = me.get("id").asText();
            apiPatch(owner, BASE + "/" + id + "/members/" + ownerUserId, Map.of("role", "ADMIN"), 409);
        }
    }

    @Nested
    @DisplayName("security")
    class Security {

        @Test
        @DisplayName("requires authentication (401 without a token)")
        void requiresAuth() throws Exception {
            apiGet(null, BASE, 401);
        }

        @Test
        @DisplayName("a non-member cannot read another user's org (403, deny-by-default)")
        void nonMemberForbidden() throws Exception {
            String id = createOrg(owner, "Private Org");
            String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
            apiGet(stranger, BASE + "/" + id, 403);
        }

        @Test
        @DisplayName("a MEMBER cannot update the org (requires ADMIN+)")
        void memberCannotUpdate() throws Exception {
            String id = createOrg(owner, "Governed Org");
            String member = addMember(owner, id, "MEMBER");
            apiPatch(member, BASE + "/" + id, Map.of("name", "Hijacked"), 403);
        }

        @Test
        @DisplayName("an ADMIN cannot delete the org (requires OWNER)")
        void adminCannotDelete() throws Exception {
            String id = createOrg(owner, "Owned Org");
            String admin = addMember(owner, id, "ADMIN");
            apiDelete(admin, BASE + "/" + id, 403);
        }
    }
}
