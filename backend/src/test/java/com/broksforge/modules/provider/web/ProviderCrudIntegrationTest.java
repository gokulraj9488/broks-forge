package com.broksforge.modules.provider.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider registry CRUD (Provider abstraction milestone), plus the Agent-side inheritance
 * seam: a new agent that references a providerId with no endpointUrl inherits the provider's
 * base URL, and an agent linked to a provider with no model override reports the provider's
 * default model as its effective configuration.
 */
@DisplayName("Provider CRUD & Agent inheritance")
class ProviderCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Provider Org");
        projectId = createProject(owner, orgId, "Provider Project");
        base = projectBase(orgId, projectId) + "/providers";
    }

    @Test
    @DisplayName("creates a provider and never returns the API key, only a hint")
    void createsProviderWithoutLeakingApiKey() throws Exception {
        JsonNode provider = apiPost(owner, base, body(
                "name", "Groq Production",
                "type", "GROQ",
                "baseUrl", "https://api.groq.com/openai/v1/chat/completions",
                "authType", "BEARER_TOKEN",
                "apiKey", "sk-live-supersecretvalue1234",
                "defaultModel", "llama-3.3-70b-versatile"), 201);

        assertThat(provider.get("name").asText()).isEqualTo("Groq Production");
        assertThat(provider.get("type").asText()).isEqualTo("GROQ");
        assertThat(provider.get("defaultModel").asText()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(provider.get("apiKeyConfigured").asBoolean()).isTrue();
        assertThat(provider.get("apiKeyHint").asText()).isEqualTo("••••1234");
        assertThat(provider.toString()).doesNotContain("supersecretvalue");
        assertThat(provider.has("encryptedApiKey")).isFalse();
        assertThat(provider.get("linkedAgentCount").asInt()).isZero();
    }

    @Test
    @DisplayName("lists, gets, updates and deletes a provider")
    void fullCrudLifecycle() throws Exception {
        String providerId = idOf(apiPost(owner, base, body(
                "name", "OpenAI Default", "type", "OPENAI",
                "baseUrl", "https://api.openai.com/v1/chat/completions", "authType", "NONE"), 201));

        assertThat(apiGet(owner, base, 200).get("totalElements").asInt()).isEqualTo(1);
        assertThat(apiGet(owner, base + "/" + providerId, 200).get("name").asText()).isEqualTo("OpenAI Default");

        JsonNode updated = apiPatch(owner, base + "/" + providerId,
                body("defaultModel", "gpt-4.1"), 200);
        assertThat(updated.get("defaultModel").asText()).isEqualTo("gpt-4.1");

        apiDelete(owner, base + "/" + providerId, 204);
        assertThat(apiGet(owner, base + "/" + providerId, 404));
    }

    @Test
    @DisplayName("rejects deleting a provider that still has linked agents")
    void rejectsDeleteWhenAgentsLinked() throws Exception {
        String providerId = idOf(apiPost(owner, base, body(
                "name", "Groq Shared", "type", "GROQ",
                "baseUrl", "https://api.groq.com/openai/v1/chat/completions", "authType", "NONE",
                "defaultModel", "llama-3.3-70b-versatile"), 201));

        apiPost(owner, projectBase(orgId, projectId) + "/agents", body(
                "name", "Linked Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "authType", "NONE", "providerId", providerId), 201);

        apiDelete(owner, base + "/" + providerId, 409);
    }

    @Test
    @DisplayName("a new agent referencing a provider with no endpointUrl inherits the provider's base URL")
    void agentInheritsEndpointFromProvider() throws Exception {
        String providerId = idOf(apiPost(owner, base, body(
                "name", "Groq Shared", "type", "GROQ",
                "baseUrl", "https://api.groq.com/openai/v1/chat/completions", "authType", "NONE",
                "defaultModel", "llama-3.3-70b-versatile"), 201));

        JsonNode agent = apiPost(owner, projectBase(orgId, projectId) + "/agents", body(
                "name", "Inheriting Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "authType", "NONE", "providerId", providerId), 201);

        assertThat(agent.get("endpointUrl").asText()).isEqualTo("https://api.groq.com/openai/v1/chat/completions");

        JsonNode providerAfter = apiGet(owner, base + "/" + providerId, 200);
        assertThat(providerAfter.get("linkedAgentCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("registers a Google AI Studio provider and an agent inheriting its model-in-URL endpoint")
    void registersGoogleAiStudioProviderAndInheritingAgent() throws Exception {
        JsonNode provider = apiPost(owner, base, body(
                "name", "Google AI Studio", "type", "GOOGLE_GEMINI",
                "baseUrl", "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
                "authType", "API_KEY", "apiKey", "sk-google-test-key-1234",
                "defaultModel", "gemini-2.5-flash",
                "supportedModels", java.util.List.of("gemini-2.5-flash", "gemini-2.5-pro")), 201);

        assertThat(provider.get("type").asText()).isEqualTo("GOOGLE_GEMINI");
        assertThat(provider.get("apiKeyHint").asText()).isEqualTo("••••1234");

        JsonNode agent = apiPost(owner, projectBase(orgId, projectId) + "/agents", body(
                "name", "Gemini Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "authType", "NONE", "providerId", idOf(provider)), 201);

        assertThat(agent.get("endpointUrl").asText())
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent");
    }

    @Test
    @DisplayName("an agent's endpointOverride wins over the provider's base URL")
    void agentEndpointOverrideWinsOverProvider() throws Exception {
        String providerId = idOf(apiPost(owner, base, body(
                "name", "Groq Shared", "type", "GROQ",
                "baseUrl", "https://api.groq.com/openai/v1/chat/completions", "authType", "NONE"), 201));

        JsonNode agent = apiPost(owner, projectBase(orgId, projectId) + "/agents", body(
                "name", "Overriding Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "authType", "NONE", "providerId", providerId,
                "endpointOverride", "https://custom.groq-proxy.internal/v1/chat/completions"), 201);

        assertThat(agent.get("endpointUrl").asText())
                .isEqualTo("https://custom.groq-proxy.internal/v1/chat/completions");
    }

    @Test
    @DisplayName("registering an agent without an endpointUrl or a providerId is rejected")
    void rejectsAgentWithNeitherEndpointNorProvider() throws Exception {
        apiPost(owner, projectBase(orgId, projectId) + "/agents", body(
                "name", "No Target Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "authType", "NONE"), 400);
    }

    @Test
    @DisplayName("a plain agent with an explicit endpointUrl and no providerId still registers exactly as before")
    void backwardCompatibleAgentWithoutProvider() throws Exception {
        JsonNode agent = apiPost(owner, projectBase(orgId, projectId) + "/agents", body(
                "name", "Legacy Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "authType", "NONE", "endpointUrl", "http://127.0.0.1:9/"), 201);
        assertThat(agent.get("endpointUrl").asText()).isEqualTo("http://127.0.0.1:9/");
        assertThat(agent.get("providerId") == null || agent.get("providerId").isNull()).isTrue();
    }

    @Test
    @DisplayName("duplicates a provider with an independent id, same config, and a fresh unknown health status")
    void duplicatesProvider() throws Exception {
        JsonNode original = apiPost(owner, base, body(
                "name", "Groq Original", "type", "GROQ",
                "baseUrl", "https://api.groq.com/openai/v1/chat/completions", "authType", "BEARER_TOKEN",
                "apiKey", "sk-original-secret-9999", "defaultModel", "llama-3.3-70b-versatile"), 201);

        JsonNode copy = apiPost(owner, base + "/" + idOf(original) + "/duplicate", null, 201);

        assertThat(copy.get("id").asText()).isNotEqualTo(idOf(original));
        assertThat(copy.get("name").asText()).isEqualTo("Groq Original (copy)");
        assertThat(copy.get("type").asText()).isEqualTo("GROQ");
        assertThat(copy.get("defaultModel").asText()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(copy.get("apiKeyConfigured").asBoolean()).isTrue();
        assertThat(copy.get("apiKeyHint").asText()).isEqualTo("••••9999");
        assertThat(copy.get("healthStatus").asText()).isEqualTo("UNKNOWN");
        assertThat(copy.get("enabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("enables and disables a provider")
    void enablesAndDisablesProvider() throws Exception {
        String providerId = idOf(apiPost(owner, base, body(
                "name", "Toggle Provider", "type", "OPENAI",
                "baseUrl", "https://api.openai.com/v1/chat/completions", "authType", "NONE"), 201));

        JsonNode created = apiGet(owner, base + "/" + providerId, 200);
        assertThat(created.get("enabled").asBoolean()).isTrue();

        JsonNode disabled = apiPost(owner, base + "/" + providerId + "/disable", null, 200);
        assertThat(disabled.get("enabled").asBoolean()).isFalse();

        JsonNode enabled = apiPost(owner, base + "/" + providerId + "/enable", null, 200);
        assertThat(enabled.get("enabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a disabled provider's linked agent cannot be run — rejected before any request is built")
    void disabledProviderBlocksAgentInvocation() throws Exception {
        String providerId = idOf(apiPost(owner, base, body(
                "name", "Disabled-Soon Provider", "type", "GROQ",
                "baseUrl", "http://127.0.0.1:9/", "authType", "NONE"), 201));
        String agentId = idOf(apiPost(owner, projectBase(orgId, projectId) + "/agents", body(
                "name", "Provider-Linked Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "authType", "NONE", "providerId", providerId), 201));
        String datasetId = createDatasetWithItems(owner, orgId, projectId, "Toggle Dataset");
        String jobId = idOf(apiPost(owner, projectBase(orgId, projectId) + "/evaluation-jobs",
                body("name", "Toggle Job", "agentId", agentId, "datasetId", datasetId), 201));

        apiPost(owner, base + "/" + providerId + "/disable", null, 200);

        apiPost(owner, projectBase(orgId, projectId) + "/evaluation-jobs/" + jobId + "/run", null, 409);
    }

    @Test
    @DisplayName("enforces tenant isolation and auth")
    void isolationAndAuth() throws Exception {
        String providerId = idOf(apiPost(owner, base, body(
                "name", "Isolated Provider", "type", "OPENAI",
                "baseUrl", "https://api.openai.com/v1/chat/completions", "authType", "NONE"), 201));
        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        apiGet(stranger, base, 403);
        apiGet(owner, base + "/" + java.util.UUID.randomUUID(), 404);
        apiGet(null, base + "/" + providerId, 401);
    }
}
