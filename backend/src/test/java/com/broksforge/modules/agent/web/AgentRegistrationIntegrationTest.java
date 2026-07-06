package com.broksforge.modules.agent.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end agent registration against a real database, covering the two production incidents
 * this suite exists to prevent regressing:
 * <ul>
 *   <li>slug reuse after soft-delete (the V30 partial-unique-index fix), and</li>
 *   <li>the {@code credentialConfigured} readiness signal + credential-secret confidentiality.</li>
 * </ul>
 */
@DisplayName("Agent registration (end-to-end)")
class AgentRegistrationIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private String agentsUrl;

    @BeforeEach
    void setUp() throws Exception {
        bearer = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        String orgId = idOf(post2xx("/api/v1/organizations", Map.of("name", "IT Org")));
        String projectId = idOf(post2xx("/api/v1/organizations/" + orgId + "/projects", Map.of("name", "IT Project")));
        agentsUrl = "/api/v1/organizations/" + orgId + "/projects/" + projectId + "/agents";
    }

    private Map<String, Object> agentBody(String name) {
        return Map.of(
                "name", name,
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "https://api.groq.com/openai/v1/chat/completions",
                "authType", "API_KEY",
                "capabilities", Map.of(
                        "streaming", true, "toolCalling", true, "structuredOutput", true,
                        "reasoning", true, "memory", false, "rag", false, "multiAgent", false));
    }

    @Test
    @DisplayName("registers an agent needing auth as not-yet-credentialed")
    void registersAgent() throws Exception {
        JsonNode agent = objectMapper.readTree(post2xx(agentsUrl, agentBody("Groq Agent")));
        assertThat(agent.get("slug").asText()).isEqualTo("groq-agent");
        assertThat(agent.get("authType").asText()).isEqualTo("API_KEY");
        assertThat(agent.get("credentialConfigured").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a slug frees up after soft-delete and can be reused (V30)")
    void slugReuseAfterSoftDelete() throws Exception {
        String id = idOf(post2xx(agentsUrl, agentBody("Groq Agent")));
        mockMvc.perform(delete(agentsUrl + "/" + id).header("Authorization", "Bearer " + bearer))
                .andExpect(status().isNoContent());

        // Previously threw "duplicate key value violates unique constraint" (409); must now succeed.
        JsonNode recreated = objectMapper.readTree(post2xx(agentsUrl, agentBody("Groq Agent")));
        assertThat(recreated.get("slug").asText()).isEqualTo("groq-agent");
    }

    @Test
    @DisplayName("live duplicate names auto-suffix rather than collide")
    void liveDuplicateAutoSuffixes() throws Exception {
        post2xx(agentsUrl, agentBody("Groq Agent"));
        JsonNode second = objectMapper.readTree(post2xx(agentsUrl, agentBody("Groq Agent")));
        assertThat(second.get("slug").asText()).isEqualTo("groq-agent-2");
    }

    @Test
    @DisplayName("configuring a credential flips credentialConfigured and never returns the secret")
    void credentialLifecycle() throws Exception {
        String id = idOf(post2xx(agentsUrl, agentBody("Groq Agent")));

        String secret = "gsk_super_secret_ABCDEFGH1234";
        mockMvc.perform(post(agentsUrl + "/" + id + "/credentials")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "label", "Groq key", "authType", "API_KEY", "secret", secret,
                                "headerName", "Authorization", "headerPrefix", "Bearer"))))
                .andExpect(status().is2xxSuccessful());

        String credentials = mockMvc.perform(get(agentsUrl + "/" + id + "/credentials")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(credentials).doesNotContain(secret);          // secret is write-only
        assertThat(objectMapper.readTree(credentials).get(0).get("secretHint").asText()).isNotBlank();

        JsonNode agent = objectMapper.readTree(mockMvc.perform(get(agentsUrl + "/" + id)
                        .header("Authorization", "Bearer " + bearer))
                .andReturn().getResponse().getContentAsString());
        assertThat(agent.get("credentialConfigured").asBoolean()).isTrue();
    }

    // --- helpers ---------------------------------------------------------

    private String post2xx(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    private String idOf(String json) throws Exception {
        return objectMapper.readTree(json).get("id").asText();
    }
}
