package com.broksforge.modules.report.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Report export + audit trail: rendering a report in each format returns a downloadable attachment
 * and records an audit row (content is re-rendered from live data, never stored — ADR 0009).
 * Covers list/get, validation, tenant isolation and auth.
 */
@DisplayName("Report export & audit")
class ReportIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;
    private String jobId;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Report Org");
        projectId = createProject(owner, orgId, "Report Project");
        base = projectBase(orgId, projectId) + "/reports";
        String agentId = idOf(apiPost(owner, projectBase(orgId, projectId) + "/agents", Map.of(
                "name", "Report Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "endpointUrl", "http://127.0.0.1:9/", "authType", "NONE"), 201));
        String datasetId = createDatasetWithItems(owner, orgId, projectId, "Report Dataset");
        jobId = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Report Job");
        // run it so the report has a real (completed) job to render
        apiPost(owner, projectBase(orgId, projectId) + "/evaluation-jobs/" + jobId + "/run", null, 200);
    }

    private void export(String format) throws Exception {
        call("POST", owner, base + "/export",
                Map.of("type", "EVALUATION_JOB", "format", format, "targetId", jobId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
    }

    @Test
    @DisplayName("exports a job report as JSON, CSV and HTML (downloadable attachment)")
    void exportsAllFormats() throws Exception {
        export("JSON");
        export("CSV");
        export("HTML");
    }

    @Test
    @DisplayName("records an audit row per export, readable via list and get")
    void recordsAuditTrail() throws Exception {
        export("JSON");
        JsonNode page = apiGet(owner, base, 200);
        assertThat(page.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);

        String reportId = page.get("content").get(0).get("id").asText();
        JsonNode report = apiGet(owner, base + "/" + reportId, 200);
        assertThat(report.get("type").asText()).isEqualTo("EVALUATION_JOB");
        assertThat(report.get("targetId").asText()).isEqualTo(jobId);
    }

    @Test
    @DisplayName("rejects an export missing required fields (400)")
    void rejectsInvalidExport() throws Exception {
        apiPost(owner, base + "/export", Map.of("format", "JSON", "targetId", jobId), 400);      // no type
        apiPost(owner, base + "/export", Map.of("type", "EVALUATION_JOB", "format", "JSON"), 400); // no targetId
    }

    @Test
    @DisplayName("returns 404 for an unknown report id and enforces auth")
    void notFoundAndAuth() throws Exception {
        apiGet(owner, base + "/" + UUID.randomUUID(), 404);
        apiGet(null, base, 401);
    }

    @Test
    @DisplayName("a non-member cannot access reports (403)")
    void nonMemberForbidden() throws Exception {
        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        apiGet(stranger, base, 403);
    }
}
