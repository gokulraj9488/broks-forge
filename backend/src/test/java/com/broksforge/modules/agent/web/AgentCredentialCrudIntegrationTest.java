package com.broksforge.modules.agent.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent credential management: per-auth-type field validation, encryption confidentiality (the
 * secret is write-only; only a masked hint is returned), single-active-credential semantics,
 * in-place update with optional secret rotation, deletion, the readiness signal, and access control.
 *
 * <p>The agent points at {@code 127.0.0.1:9} so the one dry-run "test connection" call fails fast
 * (connection refused) rather than reaching the network.</p>
 */
@DisplayName("Agent credential CRUD")
class AgentCredentialCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String agentBase;   // .../agents
    private String agentId;
    private String creds;       // credentials base

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Cred Org");
        projectId = createProject(owner, orgId, "Cred Project");
        agentBase = projectBase(orgId, projectId) + "/agents";
        agentId = idOf(apiPost(owner, agentBase, Map.of(
                "name", "Credentialed Agent",
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "http://127.0.0.1:9/",
                "authType", "NONE"), 201));
        creds = agentBase + "/" + agentId + "/credentials";
    }

    @Test
    @DisplayName("sets an API-key credential, flips readiness, and never returns the secret")
    void setsApiKeyCredential() throws Exception {
        String secret = "gsk_live_secret_ABCD1234";
        JsonNode cred = apiPost(owner, creds, Map.of(
                "label", "Prod key", "authType", "API_KEY", "secret", secret,
                "headerName", "Authorization", "headerPrefix", "Bearer"), 201);
        assertThat(cred.get("active").asBoolean()).isTrue();
        assertThat(cred.get("secretHint").asText()).endsWith("1234").doesNotContain("gsk_live");
        assertThat(cred.toString()).doesNotContain(secret);

        JsonNode agent = apiGet(owner, agentBase + "/" + agentId, 200);
        assertThat(agent.get("authType").asText()).isEqualTo("API_KEY");
        assertThat(agent.get("credentialConfigured").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("enforces per-auth-type field requirements")
    void enforcesPerTypeFields() throws Exception {
        apiPost(owner, creds, Map.of("authType", "NONE", "secret", "should-not-be-here"), 400);
        apiPost(owner, creds, Map.of("authType", "API_KEY"), 400);                       // secret required
        apiPost(owner, creds, Map.of("authType", "BEARER_TOKEN"), 400);                  // secret required
        apiPost(owner, creds, Map.of("authType", "BASIC_AUTH", "secret", "pw"), 400);    // username required
        apiPost(owner, creds, Map.of("authType", "CUSTOM_HEADER", "secret", "v"), 400);  // headerName required
    }

    @Test
    @DisplayName("lists credential metadata with a masked hint and no secret")
    void listsMasked() throws Exception {
        String secret = "sk_test_masked_WXYZ9876";
        apiPost(owner, creds, Map.of("authType", "API_KEY", "secret", secret, "headerName", "Authorization"), 201);
        String raw = call("GET", owner, creds, null).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain(secret);
        JsonNode list = objectMapper.readTree(raw);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("secretHint").asText()).isNotBlank();
    }

    @Test
    @DisplayName("setting a new credential deactivates the prior one (single active)")
    void secondDeactivatesFirst() throws Exception {
        apiPost(owner, creds, Map.of("authType", "API_KEY", "secret", "key_AAAA", "headerName", "Authorization"), 201);
        apiPost(owner, creds, Map.of("authType", "BEARER_TOKEN", "secret", "tok_BBBB"), 201);

        JsonNode list = objectMapper.readTree(call("GET", owner, creds, null).andReturn().getResponse().getContentAsString());
        assertThat(list).hasSize(2);
        long active = 0;
        for (JsonNode c : list) {
            if (c.get("active").asBoolean()) {
                active++;
            }
        }
        assertThat(active).isEqualTo(1);
    }

    @Test
    @DisplayName("update rotates the secret when a new one is supplied")
    void updateRotatesSecret() throws Exception {
        JsonNode cred = apiPost(owner, creds, Map.of(
                "authType", "API_KEY", "secret", "key_first_AAAA", "headerName", "Authorization"), 201);
        String before = cred.get("secretHint").asText();
        JsonNode updated = apiPut(owner, creds + "/" + idOf(cred), Map.of(
                "authType", "API_KEY", "secret", "key_second_BBBB", "headerName", "Authorization"), 200);
        assertThat(updated.get("secretHint").asText()).isNotEqualTo(before).endsWith("BBBB");
    }

    @Test
    @DisplayName("update keeps the stored secret when the secret field is blank")
    void updateKeepsSecretWhenBlank() throws Exception {
        JsonNode cred = apiPost(owner, creds, Map.of(
                "authType", "API_KEY", "secret", "key_keep_CDEF", "headerName", "Authorization"), 201);
        String before = cred.get("secretHint").asText();
        JsonNode updated = apiPut(owner, creds + "/" + idOf(cred), body(
                "authType", "API_KEY", "secret", "", "headerName", "Authorization", "label", "renamed"), 200);
        assertThat(updated.get("secretHint").asText()).isEqualTo(before);
        assertThat(updated.get("label").asText()).isEqualTo("renamed");
    }

    @Test
    @DisplayName("deletes a credential and clears readiness")
    void deletesCredential() throws Exception {
        JsonNode cred = apiPost(owner, creds, Map.of(
                "authType", "API_KEY", "secret", "key_del_GHIJ", "headerName", "Authorization"), 201);
        apiDelete(owner, creds + "/" + idOf(cred), 204);

        JsonNode list = objectMapper.readTree(call("GET", owner, creds, null).andReturn().getResponse().getContentAsString());
        assertThat(list).isEmpty();
        assertThat(apiGet(owner, agentBase + "/" + agentId, 200).get("credentialConfigured").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("dry-run test-connection returns a result (fails fast against an unreachable endpoint)")
    void testDraftReturnsResult() throws Exception {
        JsonNode result = apiPost(owner, creds + "/test", Map.of(
                "authType", "API_KEY", "secret", "key_test_KLMN", "headerName", "Authorization",
                "headerPrefix", "Bearer"), 200);
        assertThat(result.has("success")).isTrue();
        assertThat(result.get("success").asBoolean()).isFalse(); // 127.0.0.1:9 refuses the connection
    }

    @Test
    @DisplayName("a MEMBER cannot manage credentials (requires ADMIN)")
    void memberCannotManage() throws Exception {
        String member = addMember(owner, orgId, "MEMBER");
        apiPost(member, creds, Map.of("authType", "API_KEY", "secret", "x", "headerName", "Authorization"), 403);
    }
}
