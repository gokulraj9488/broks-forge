package com.broksforge.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for full-context integration tests. Boots the real Spring context against a
 * <b>real PostgreSQL 16</b> (the same image production uses) via a singleton Testcontainer, so
 * Flyway migrations run and Hibernate's {@code ddl-auto=validate} exercises the true schema.
 *
 * <p>The container is started once for the whole test run (static singleton pattern) and shared by
 * every subclass, which is far faster than a container per class. It is never explicitly stopped —
 * the Ryuk sidecar / JVM shutdown reclaims it.</p>
 *
 * <p>Besides the container wiring this class provides two families of test helpers, kept here so the
 * CRUD suites stay terse and read like the behaviour they assert:</p>
 * <ul>
 *   <li><b>Generic HTTP</b> — {@link #apiPost}, {@link #apiGet}, {@link #apiPatch}, {@link #apiPut},
 *       {@link #apiDelete} issue an authenticated JSON request and assert the expected status,
 *       returning the parsed response body. {@link #call} exposes the raw {@link ResultActions} for
 *       bespoke assertions (e.g. header checks).</li>
 *   <li><b>Fixture creators</b> — {@link #createOrg}, {@link #createProject}, {@link #registerAgent},
 *       {@link #createDatasetWithItems}, {@link #createPromptWithVersion}, {@link #createProfile},
 *       {@link #addMember} build the minimal valid prerequisite graph and return the new ids, so a
 *       test can set up cross-module state (e.g. "an evaluation job needs an agent + a dataset with
 *       a version") in one line each.</li>
 * </ul>
 * The helpers never set server-controlled fields; they post the same request DTOs a real client
 * would, so they exercise the true validation/mapping/security path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** A unique e-mail so each test is independent of the shared database. */
    protected String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    /** Registers a fresh user and returns its access token (bearer, without the scheme prefix). */
    protected String registerAndGetToken(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterBody(email, password, "Test", "User"));
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }

    public record RegisterBody(String email, String password, String firstName, String lastName) {
    }

    // ====================================================================================
    // Generic authenticated JSON HTTP helpers
    // ====================================================================================

    /**
     * Issues an authenticated JSON request and returns the raw {@link ResultActions} for callers
     * that need to assert on more than the parsed body (status, headers, content type).
     *
     * @param method one of GET/POST/PUT/PATCH/DELETE (case-insensitive)
     * @param token  bearer token without the {@code Bearer } prefix; {@code null} sends no auth header
     * @param path   request path
     * @param body   request body serialised as JSON, or {@code null} for no body
     */
    protected ResultActions call(String method, String token, String path, Object body) throws Exception {
        MockHttpServletRequestBuilder rb = switch (method.toUpperCase()) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
        if (token != null) {
            rb.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            rb.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(rb);
    }

    protected JsonNode apiPost(String token, String path, Object body, int expectedStatus) throws Exception {
        return readBody(call("POST", token, path, body).andExpect(status().is(expectedStatus)));
    }

    protected JsonNode apiPut(String token, String path, Object body, int expectedStatus) throws Exception {
        return readBody(call("PUT", token, path, body).andExpect(status().is(expectedStatus)));
    }

    protected JsonNode apiPatch(String token, String path, Object body, int expectedStatus) throws Exception {
        return readBody(call("PATCH", token, path, body).andExpect(status().is(expectedStatus)));
    }

    protected JsonNode apiGet(String token, String path, int expectedStatus) throws Exception {
        return readBody(call("GET", token, path, null).andExpect(status().is(expectedStatus)));
    }

    protected void apiDelete(String token, String path, int expectedStatus) throws Exception {
        call("DELETE", token, path, null).andExpect(status().is(expectedStatus));
    }

    /** Parses the response body as JSON; returns a JSON null node for an empty body (e.g. 204). */
    protected JsonNode readBody(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString();
        if (content == null || content.isBlank()) {
            return objectMapper.nullNode();
        }
        return objectMapper.readTree(content);
    }

    protected String idOf(JsonNode json) {
        return json.get("id").asText();
    }

    /** Mutable ordered map builder so tests can include explicit nulls / optional fields (Map.of cannot). */
    protected Map<String, Object> body(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // ====================================================================================
    // Fixture creators — build the minimal valid prerequisite graph, return the new ids
    // ====================================================================================

    /** Creates an organization owned by the token's user; returns its id. */
    protected String createOrg(String token, String name) throws Exception {
        return idOf(apiPost(token, "/api/v1/organizations", Map.of("name", name), 201));
    }

    /** Creates a project in the org; returns its id. */
    protected String createProject(String token, String orgId, String name) throws Exception {
        return idOf(apiPost(token, "/api/v1/organizations/" + orgId + "/projects",
                Map.of("name", name), 201));
    }

    protected String projectBase(String orgId, String projectId) {
        return "/api/v1/organizations/" + orgId + "/projects/" + projectId;
    }

    /** Registers a minimal, valid, no-auth agent (endpoint never called unless a job auto-runs). */
    protected String registerAgent(String token, String orgId, String projectId, String name) throws Exception {
        Map<String, Object> req = Map.of(
                "name", name,
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "https://api.example.com/agents/" + UUID.randomUUID(),
                "authType", "NONE");
        return idOf(apiPost(token, projectBase(orgId, projectId) + "/agents", req, 201));
    }

    /** Creates a dataset and imports one CSV version with two items; returns the dataset id. */
    protected String createDatasetWithItems(String token, String orgId, String projectId, String name)
            throws Exception {
        String datasetId = idOf(apiPost(token, projectBase(orgId, projectId) + "/datasets",
                Map.of("name", name), 201));
        String csv = "input,expected_output\nhello,hi\nbye,ciao\n";
        apiPost(token, projectBase(orgId, projectId) + "/datasets/" + datasetId + "/versions",
                Map.of("format", "CSV", "content", csv), 201);
        return datasetId;
    }

    /** Creates a prompt with one active version; returns the prompt id. */
    protected String createPromptWithVersion(String token, String orgId, String projectId, String name)
            throws Exception {
        String promptId = idOf(apiPost(token, projectBase(orgId, projectId) + "/prompts",
                Map.of("name", name), 201));
        apiPost(token, projectBase(orgId, projectId) + "/prompts/" + promptId + "/versions",
                Map.of("template", "Answer {{question}} for {{user}}", "activate", true), 201);
        return promptId;
    }

    /** Creates a minimal evaluation profile; returns its id. */
    protected String createProfile(String token, String orgId, String projectId, String name) throws Exception {
        return idOf(apiPost(token, projectBase(orgId, projectId) + "/evaluation-profiles",
                Map.of("name", name), 201));
    }

    /**
     * Creates a PENDING evaluation job referencing the given agent + dataset (never auto-run, so no
     * outbound network call is made); returns the job id.
     */
    protected String createEvaluationJob(String token, String orgId, String projectId,
                                         String agentId, String datasetId, String name) throws Exception {
        Map<String, Object> req = Map.of("name", name, "agentId", agentId, "datasetId", datasetId);
        return idOf(apiPost(token, projectBase(orgId, projectId) + "/evaluation-jobs", req, 201));
    }

    /** Registers a new user, adds them to the org with the given role, and returns their token. */
    protected String addMember(String ownerToken, String orgId, String role) throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email, "StrongPass!2026");
        apiPost(ownerToken, "/api/v1/organizations/" + orgId + "/members",
                Map.of("email", email, "role", role), 201);
        return token;
    }
}
