package com.broksforge.modules.analytics.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-model modules that back the frontend Analytics / Insights surfaces: analytics aggregates,
 * the dashboard roll-up and global search. Focuses on empty-state correctness (zeros, not errors),
 * parameter handling and access control.
 */
@DisplayName("Analytics, Dashboard & Search (read models)")
class AnalyticsDashboardSearchIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Insights Org");
        projectId = createProject(owner, orgId, "Insights Project");
        base = projectBase(orgId, projectId);
    }

    @Test
    @DisplayName("analytics returns a well-formed empty overview when there is no data")
    void analyticsEmpty() throws Exception {
        JsonNode a = apiGet(owner, base + "/analytics", 200);
        assertThat(a.get("jobCount").asInt()).isZero();
        assertThat(a.get("runCount").asInt()).isZero();
        assertThat(a.get("passRate").asDouble()).isEqualTo(0.0);
        // avgLatencyMs is null with no data; the response omits null fields, so the node is absent
        assertThat(a.get("avgLatencyMs")).isNull();
        assertThat(a.get("trend").isArray()).isTrue();
        assertThat(a.get("trend")).isEmpty();
    }

    @Test
    @DisplayName("analytics clamps an out-of-range window")
    void analyticsClampsWindow() throws Exception {
        assertThat(apiGet(owner, base + "/analytics?windowDays=1000", 200).get("windowDays").asInt())
                .isEqualTo(365);
    }

    @Test
    @DisplayName("dashboard returns an all-zero roll-up for an empty project")
    void dashboardEmpty() throws Exception {
        JsonNode d = apiGet(owner, base + "/dashboard", 200);
        assertThat(d.get("counts").get("agents").asInt()).isZero();
        assertThat(d.get("counts").get("evaluationJobs").asInt()).isZero();
        assertThat(d.get("recentJobs").isArray()).isTrue();
        assertThat(d.get("recentJobs")).isEmpty();
    }

    @Test
    @DisplayName("analytics jobCount is scoped to BOTH organizationId and projectId, not projectId alone")
    void analyticsJobCountDoesNotLeakAcrossOrganizations() throws Exception {
        // A victim org (different owner) with a real evaluation job.
        String victimOwner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        String victimOrgId = createOrg(victimOwner, "Victim Org");
        String victimProjectId = createProject(victimOwner, victimOrgId, "Victim Project");
        String datasetId = createDatasetWithItems(victimOwner, victimOrgId, victimProjectId, "Victim Dataset");
        String agentId = registerAgent(victimOwner, victimOrgId, victimProjectId, "Victim Agent");
        createEvaluationJob(victimOwner, victimOrgId, victimProjectId, agentId, datasetId, "Victim Job");

        // The attacker is a legitimate member of their OWN org, but requests analytics through
        // their own organizationId (so requireMembership passes) with the victim's projectId.
        JsonNode result = apiGet(owner,
                "/api/v1/organizations/" + orgId + "/projects/" + victimProjectId + "/analytics", 200);
        assertThat(result.get("jobCount").asInt())
                .as("victim org's job must never be counted under the attacker's organizationId")
                .isZero();
    }

    @Test
    @DisplayName("search requires the q parameter (400) and returns empty for a blank query (200)")
    void searchParamHandling() throws Exception {
        apiGet(owner, base + "/search", 400);
        JsonNode blank = apiGet(owner, base + "/search?q=", 200);
        assertThat(blank.get("hits")).isEmpty();
    }

    @Test
    @DisplayName("search finds a matching agent")
    void searchFindsAgent() throws Exception {
        registerAgent(owner, orgId, projectId, "Findable Agent");
        JsonNode result = apiGet(owner, base + "/search?q=Findable", 200);
        assertThat(result.get("hits")).isNotEmpty();
        boolean hasAgentHit = false;
        for (JsonNode hit : result.get("hits")) {
            if ("AGENT".equals(hit.get("type").asText())) {
                hasAgentHit = true;
            }
        }
        assertThat(hasAgentHit).isTrue();
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuth() throws Exception {
        apiGet(null, base + "/analytics", 401);
        apiGet(null, base + "/dashboard", 401);
    }
}
