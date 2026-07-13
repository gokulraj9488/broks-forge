package com.broksforge.modules.evaluation.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evaluation-job lifecycle: creation with its cross-module prerequisites (an agent + a dataset with a
 * version), reference validation (missing/foreign ids, dataset-without-version, prompt-without-active-
 * version), listing/filtering, cancel, synchronous run to a terminal state, soft-delete, isolation.
 *
 * <p>The agent points at {@code 127.0.0.1:9} so the one {@code /run} executes deterministically
 * (each item's outbound call is refused) instead of reaching the network.</p>
 */
@DisplayName("Evaluation job CRUD & run")
class EvaluationJobCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;
    private String agentId;
    private String datasetId;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Eval Org");
        projectId = createProject(owner, orgId, "Eval Project");
        base = projectBase(orgId, projectId) + "/evaluation-jobs";
        agentId = idOf(apiPost(owner, projectBase(orgId, projectId) + "/agents", Map.of(
                "name", "Eval Agent",
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "http://127.0.0.1:9/",
                "authType", "NONE"), 201));
        datasetId = createDatasetWithItems(owner, orgId, projectId, "Eval Dataset");
    }

    @Test
    @DisplayName("creates a PENDING job pinning the dataset's item count")
    void createsPendingJob() throws Exception {
        JsonNode job = apiPost(owner, base, Map.of("name", "Run 1", "agentId", agentId, "datasetId", datasetId), 201);
        assertThat(job.get("status").asText()).isEqualTo("PENDING");
        assertThat(job.get("totalItems").asInt()).isEqualTo(2);
        assertThat(job.get("agentId").asText()).isEqualTo(agentId);
    }

    @Test
    @DisplayName("rejects a job missing required references (400)")
    void rejectsMissingReferences() throws Exception {
        apiPost(owner, base, Map.of("name", "No Agent", "datasetId", datasetId), 400);
        apiPost(owner, base, Map.of("name", "No Dataset", "agentId", agentId), 400);
    }

    @Test
    @DisplayName("rejects foreign/unknown agent or dataset ids (404)")
    void rejectsForeignReferences() throws Exception {
        apiPost(owner, base, Map.of("name", "Bad Agent", "agentId", UUID.randomUUID().toString(),
                "datasetId", datasetId), 404);
        apiPost(owner, base, Map.of("name", "Bad Dataset", "agentId", agentId,
                "datasetId", UUID.randomUUID().toString()), 404);
    }

    @Test
    @DisplayName("rejects a dataset that has no version (400)")
    void rejectsDatasetWithoutVersion() throws Exception {
        String emptyDataset = idOf(apiPost(owner, projectBase(orgId, projectId) + "/datasets",
                Map.of("name", "No Version DS"), 201));
        apiPost(owner, base, Map.of("name", "No Version", "agentId", agentId, "datasetId", emptyDataset), 400);
    }

    @Test
    @DisplayName("rejects a prompt with no active version (409)")
    void rejectsPromptWithoutActiveVersion() throws Exception {
        String promptNoVersion = idOf(apiPost(owner, projectBase(orgId, projectId) + "/prompts",
                Map.of("name", "Empty Prompt"), 201));
        apiPost(owner, base, Map.of("name", "Bad Prompt", "agentId", agentId, "datasetId", datasetId,
                "promptId", promptNoVersion), 409);
    }

    @Test
    @DisplayName("rejects a hosted-provider agent with no resolvable model (400), instead of a 400 later from the provider")
    void rejectsHostedProviderAgentWithoutModel() throws Exception {
        String groqAgent = idOf(apiPost(owner, projectBase(orgId, projectId) + "/agents", Map.of(
                "name", "No-Model Groq Agent",
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "https://api.groq.com/openai/v1/chat/completions",
                "authType", "NONE"), 201));

        apiPost(owner, base, Map.of("name", "No Model", "agentId", groqAgent, "datasetId", datasetId), 400);
    }

    @Test
    @DisplayName("rejects a provider name typed into the model field (400), instead of a 404 later from the provider")
    void rejectsProviderNameAsModel() throws Exception {
        String groqAgent = idOf(apiPost(owner, projectBase(orgId, projectId) + "/agents", Map.of(
                "name", "Mistyped Model Agent",
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "https://api.groq.com/openai/v1/chat/completions",
                "authType", "NONE"), 201));

        apiPost(owner, base, Map.of("name", "Bad Model", "agentId", groqAgent,
                "datasetId", datasetId, "model", "Groq"), 400);
        apiPost(owner, base, Map.of("name", "Bad Model 2", "agentId", groqAgent,
                "datasetId", datasetId, "model", "OpenAI"), 400);
        apiPost(owner, base, Map.of("name", "Bad Model 3", "agentId", groqAgent,
                "datasetId", datasetId, "model", "Claude"), 400);
    }

    @Test
    @DisplayName("an explicit model on the job satisfies a hosted-provider agent")
    void explicitModelSatisfiesHostedProviderAgent() throws Exception {
        String groqAgent = idOf(apiPost(owner, projectBase(orgId, projectId) + "/agents", Map.of(
                "name", "Explicit Model Groq Agent",
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "https://api.groq.com/openai/v1/chat/completions",
                "authType", "NONE"), 201));

        JsonNode job = apiPost(owner, base, Map.of("name", "Has Model", "agentId", groqAgent,
                "datasetId", datasetId, "model", "llama-3.3-70b-versatile"), 201);
        assertThat(job.get("model").asText()).isEqualTo("llama-3.3-70b-versatile");
    }

    @Test
    @DisplayName("the agent's active version model is copied onto the job when the request omits one")
    void copiesModelFromActiveAgentVersion() throws Exception {
        String groqAgent = idOf(apiPost(owner, projectBase(orgId, projectId) + "/agents", Map.of(
                "name", "Versioned Groq Agent",
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "https://api.groq.com/openai/v1/chat/completions",
                "authType", "NONE"), 201));
        apiPost(owner, projectBase(orgId, projectId) + "/agents/" + groqAgent + "/versions", body(
                "versionNumber", "1.0.0",
                "model", "llama-3.3-70b-versatile",
                "provider", "GROQ",
                "environment", "PRODUCTION",
                "activate", true,
                "rollbackReady", true), 201);

        JsonNode job = apiPost(owner, base, Map.of("name", "Inherits Model", "agentId", groqAgent,
                "datasetId", datasetId), 201);
        assertThat(job.get("model").asText()).isEqualTo("llama-3.3-70b-versatile");
    }

    @Test
    @DisplayName("creates a job referencing a profile and a prompt with an active version")
    void createsWithProfileAndPrompt() throws Exception {
        String profileId = createProfile(owner, orgId, projectId, "Job Profile");
        String promptId = createPromptWithVersion(owner, orgId, projectId, "Job Prompt");
        JsonNode job = apiPost(owner, base, Map.of(
                "name", "Full Job", "agentId", agentId, "datasetId", datasetId,
                "promptId", promptId, "profileId", profileId), 201);
        assertThat(job.get("profileId").asText()).isEqualTo(profileId);
        assertThat(job.get("promptId").asText()).isEqualTo(promptId);
        assertThat(job.get("profileVersionNumber").asInt()).isEqualTo(1);
        assertThat(job.get("profileVersionId").asText()).isNotBlank();
    }

    @Test
    @DisplayName("a job's pinned profile version never changes even after the profile is edited later (Milestone 1)")
    void pinnedProfileVersionSurvivesLaterProfileEdits() throws Exception {
        String profilesBase = projectBase(orgId, projectId) + "/evaluation-profiles";
        JsonNode profile = apiPost(owner, profilesBase, body("name", "Evolving Job Profile",
                "metrics", java.util.List.of(Map.of("type", "EXACT_MATCH"))), 201);
        String profileId = profile.get("id").asText();

        JsonNode job = apiPost(owner, base, Map.of(
                "name", "Pinned Job", "agentId", agentId, "datasetId", datasetId, "profileId", profileId), 201);
        assertThat(job.get("profileVersionNumber").asInt()).isEqualTo(1);
        String pinnedVersionId = job.get("profileVersionId").asText();

        // Editing the profile's metrics after the job was created bumps the profile to version 2...
        apiPatch(owner, profilesBase + "/" + profileId,
                body("metrics", java.util.List.of(Map.of("type", "NON_EMPTY"))), 200);

        // ...but the already-created job must still point at version 1 — never version 2.
        JsonNode reloadedJob = apiGet(owner, base + "/" + job.get("id").asText(), 200);
        assertThat(reloadedJob.get("profileVersionNumber").asInt()).isEqualTo(1);
        assertThat(reloadedJob.get("profileVersionId").asText()).isEqualTo(pinnedVersionId);
    }

    @Test
    @DisplayName("lists, filters and gets jobs")
    void listFilterGet() throws Exception {
        String id = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Alpha Job");
        createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Beta Job");

        assertThat(apiGet(owner, base, 200).get("totalElements").asInt()).isEqualTo(2);
        assertThat(apiGet(owner, base + "?q=Alpha", 200).get("totalElements").asInt()).isEqualTo(1);
        assertThat(apiGet(owner, base + "?agentId=" + agentId, 200).get("totalElements").asInt()).isEqualTo(2);
        assertThat(apiGet(owner, base + "/" + id, 200).get("id").asText()).isEqualTo(id);
    }

    @Test
    @DisplayName("cancels a PENDING job; cancelling again is 409")
    void cancelsPendingJob() throws Exception {
        String id = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Cancellable");
        JsonNode cancelled = apiPost(owner, base + "/" + id + "/cancel", null, 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        apiPost(owner, base + "/" + id + "/cancel", null, 409);
    }

    @Test
    @DisplayName("runs a job synchronously to a terminal state and records runs")
    void runsToTerminal() throws Exception {
        String id = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Runnable");
        JsonNode ran = apiPost(owner, base + "/" + id + "/run", null, 200);
        assertThat(ran.get("status").asText()).isIn("COMPLETED", "FAILED");

        // the runs listing is reachable and paginated
        apiGet(owner, base + "/" + id + "/runs", 200);
    }

    @Test
    @DisplayName("soft-deletes (ADMIN); a MEMBER cannot delete")
    void softDeleteAndPermissions() throws Exception {
        String id = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Doomed Job");
        String member = addMember(owner, orgId, "MEMBER");
        apiDelete(member, base + "/" + id, 403);
        apiDelete(owner, base + "/" + id, 204);
        apiGet(owner, base + "/" + id, 404);
    }

    @Test
    @DisplayName("enforces tenant isolation and auth")
    void isolationAndAuth() throws Exception {
        String id = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Mine");
        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        apiGet(stranger, base, 403);
        apiGet(owner, base + "/" + UUID.randomUUID(), 404);
        apiGet(null, base + "/" + id, 401);
    }
}
