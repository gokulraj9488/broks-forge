package com.broksforge.modules.regression.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression-check CRUD: create a baseline-vs-candidate comparison over two real evaluation jobs,
 * the same-job guard, foreign-job rejection, read/list, soft-delete, tenant isolation and permissions.
 */
@DisplayName("Regression check CRUD")
class RegressionCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;
    private String baselineJobId;
    private String candidateJobId;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Reg Org");
        projectId = createProject(owner, orgId, "Reg Project");
        base = projectBase(orgId, projectId) + "/regression-checks";
        String agentId = registerAgent(owner, orgId, projectId, "Reg Agent");
        String datasetId = createDatasetWithItems(owner, orgId, projectId, "Reg Dataset");
        baselineJobId = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Baseline");
        candidateJobId = createEvaluationJob(owner, orgId, projectId, agentId, datasetId, "Candidate");
    }

    @Test
    @DisplayName("creates a regression check with a verdict and findings")
    void createsCheck() throws Exception {
        JsonNode check = apiPost(owner, base, Map.of(
                "name", "Deploy check", "baselineJobId", baselineJobId, "candidateJobId", candidateJobId), 201);
        assertThat(check.has("regressed")).isTrue();
        // findings is a Map<String,Object> keyed by dimension -> a JSON object, not an array
        assertThat(check.get("findings").isObject()).isTrue();
        assertThat(check.get("baselineJobId").asText()).isEqualTo(baselineJobId);
    }

    @Test
    @DisplayName("rejects comparing a job against itself (400)")
    void rejectsSameJob() throws Exception {
        apiPost(owner, base, Map.of(
                "name", "Self", "baselineJobId", baselineJobId, "candidateJobId", baselineJobId), 400);
    }

    @Test
    @DisplayName("rejects a foreign/unknown candidate job (404)")
    void rejectsForeignJob() throws Exception {
        apiPost(owner, base, Map.of(
                "name", "Bad", "baselineJobId", baselineJobId, "candidateJobId", UUID.randomUUID().toString()), 404);
    }

    @Test
    @DisplayName("rejects a missing baseline job id (400)")
    void rejectsMissingBaseline() throws Exception {
        apiPost(owner, base, Map.of("name", "No Baseline", "candidateJobId", candidateJobId), 400);
    }

    @Test
    @DisplayName("reads and lists checks")
    void readAndList() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of(
                "name", "Listed", "baselineJobId", baselineJobId, "candidateJobId", candidateJobId), 201));
        assertThat(apiGet(owner, base + "/" + id, 200).get("id").asText()).isEqualTo(id);
        assertThat(apiGet(owner, base, 200).get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("soft-deletes (ADMIN); a MEMBER cannot delete")
    void softDeleteAndPermissions() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of(
                "name", "Doomed", "baselineJobId", baselineJobId, "candidateJobId", candidateJobId), 201));
        String member = addMember(owner, orgId, "MEMBER");
        apiDelete(member, base + "/" + id, 403);
        apiDelete(owner, base + "/" + id, 204);
        apiGet(owner, base + "/" + id, 404);
    }

    @Test
    @DisplayName("enforces tenant isolation and auth")
    void isolationAndAuth() throws Exception {
        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        apiGet(stranger, base, 403);
        apiGet(owner, base + "/" + UUID.randomUUID(), 404);
        apiGet(null, base, 401);
    }
}
