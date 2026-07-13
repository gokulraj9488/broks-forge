package com.broksforge.modules.advisor.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Phase-4 compute-on-read advisory modules: advisor, root-cause and debugger. They persist
 * nothing; the contract here is that they return a 200 report (with explanatory notes when input is
 * thin), resolve real subjects, are IDOR-safe (foreign subject -> 404), and require authentication.
 */
@DisplayName("Advisor, Root-cause & Debugger (compute-on-read)")
class AdvisorRootCauseDebuggerIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;
    private String agentId;
    private String promptId;
    private String baselineJob;
    private String candidateJob;
    private String checkId;
    private String runId;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Advisory Org");
        projectId = createProject(owner, orgId, "Advisory Project");
        base = projectBase(orgId, projectId);
        agentId = idOf(apiPost(owner, base + "/agents", Map.of(
                "name", "Advisory Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "endpointUrl", "http://127.0.0.1:9/", "authType", "NONE"), 201));
        promptId = createPromptWithVersion(owner, orgId, projectId, "Advisory Prompt");
        String datasetId = createDatasetWithItems(owner, orgId, projectId, "Advisory Dataset");
        baselineJob = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Baseline");
        candidateJob = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Candidate");
        // run the baseline so there is a finished run for the debugger timeline
        apiPost(owner, base + "/evaluation-jobs/" + baselineJob + "/run", null, 200);
        runId = apiGet(owner, base + "/evaluation-jobs/" + baselineJob + "/runs", 200)
                .get("content").get(0).get("id").asText();
        checkId = idOf(apiPost(owner, base + "/regression-checks", Map.of(
                "name", "Advisory Check", "baselineJobId", baselineJob, "candidateJobId", candidateJob), 201));
    }

    @Test
    @DisplayName("project advisor returns a report even with thin data")
    void advisorProject() throws Exception {
        JsonNode report = apiGet(owner, base + "/advisor", 200);
        assertThat(report.has("recommendationCount")).isTrue();
        assertThat(report.get("notes").isArray()).isTrue();
    }

    @Test
    @DisplayName("agent advisor resolves a real agent (200) and 404s a foreign one")
    void advisorAgent() throws Exception {
        apiGet(owner, base + "/advisor/agents/" + agentId, 200);
        apiGet(owner, base + "/advisor/agents/" + UUID.randomUUID(), 404);
    }

    @Test
    @DisplayName("prompt advisor resolves a real prompt (200) and 404s a foreign one")
    void advisorPrompt() throws Exception {
        apiGet(owner, base + "/advisor/prompts/" + promptId, 200);
        apiGet(owner, base + "/advisor/prompts/" + UUID.randomUUID(), 404);
    }

    @Test
    @DisplayName("root-cause diagnoses a job (200) and a regression check (200); foreign ids 404")
    void rootCause() throws Exception {
        assertThat(apiGet(owner, base + "/root-cause/jobs/" + baselineJob, 200).has("findingCount")).isTrue();
        apiGet(owner, base + "/root-cause/jobs/" + UUID.randomUUID(), 404);

        apiGet(owner, base + "/root-cause/regressions/" + checkId, 200);
        apiGet(owner, base + "/root-cause/regressions/" + UUID.randomUUID(), 404);
    }

    @Test
    @DisplayName("debugger reconstructs a timeline for a finished run; unknown ids 404")
    void debuggerTimeline() throws Exception {
        JsonNode timeline = apiGet(owner,
                base + "/debugger/jobs/" + baselineJob + "/runs/" + runId + "/timeline", 200);
        assertThat(timeline.get("stages").isArray()).isTrue();
        assertThat(timeline.get("runId").asText()).isEqualTo(runId);

        apiGet(owner, base + "/debugger/jobs/" + baselineJob + "/runs/" + UUID.randomUUID() + "/timeline", 404);
        apiGet(owner, base + "/debugger/jobs/" + UUID.randomUUID() + "/runs/" + runId + "/timeline", 404);
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuth() throws Exception {
        apiGet(null, base + "/advisor", 401);
    }
}
