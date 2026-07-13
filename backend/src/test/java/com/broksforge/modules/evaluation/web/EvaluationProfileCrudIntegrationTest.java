package com.broksforge.modules.evaluation.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evaluation-profile CRUD: create/read/update/soft-delete, slug rules, metric-spec validation
 * (regex needs a pattern; latency/cost/token metrics need a threshold), pass-threshold bounds,
 * tenant isolation and permissions.
 */
@DisplayName("Evaluation profile CRUD")
class EvaluationProfileCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Profile Org");
        projectId = createProject(owner, orgId, "Profile Project");
        base = projectBase(orgId, projectId) + "/evaluation-profiles";
    }

    @Test
    @DisplayName("creates a minimal profile with a generated slug")
    void createsMinimal() throws Exception {
        JsonNode p = apiPost(owner, base, Map.of("name", "Strict QA"), 201);
        assertThat(p.get("slug").asText()).isEqualTo("strict-qa");
    }

    @Test
    @DisplayName("creates a profile with valid metrics and a pass threshold")
    void createsWithMetrics() throws Exception {
        JsonNode p = apiPost(owner, base, body(
                "name", "Metric Profile",
                "metrics", List.of(
                        Map.of("type", "EXACT_MATCH"),
                        Map.of("type", "LATENCY", "threshold", 1000)),
                "passThreshold", 0.8), 201);
        assertThat(p.get("metrics")).hasSize(2);
        assertThat(p.get("passThreshold").asDouble()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("rejects a latency metric with no threshold (400)")
    void rejectsLatencyWithoutThreshold() throws Exception {
        apiPost(owner, base, body("name", "Bad Latency",
                "metrics", List.of(Map.of("type", "LATENCY"))), 400);
    }

    @Test
    @DisplayName("rejects a regex metric with no pattern (400)")
    void rejectsRegexWithoutPattern() throws Exception {
        apiPost(owner, base, body("name", "Bad Regex",
                "metrics", List.of(Map.of("type", "REGEX_MATCH"))), 400);
    }

    @Test
    @DisplayName("rejects a pass threshold outside [0,1] (400)")
    void rejectsOutOfRangeThreshold() throws Exception {
        apiPost(owner, base, Map.of("name", "Over", "passThreshold", 1.5), 400);
    }

    @Test
    @DisplayName("rejects a duplicate explicit slug (409) and a blank name (400)")
    void rejectsDuplicateSlugAndBlankName() throws Exception {
        apiPost(owner, base, Map.of("name", "First", "slug", "clash"), 201);
        apiPost(owner, base, Map.of("name", "Second", "slug", "clash"), 409);
        apiPost(owner, base, Map.of("name", ""), 400);
    }

    @Test
    @DisplayName("reads, updates and lists profiles")
    void readUpdateList() throws Exception {
        String id = createProfile(owner, orgId, projectId, "Editable");
        JsonNode updated = apiPatch(owner, base + "/" + id,
                Map.of("description", "now described", "passThreshold", 0.5), 200);
        assertThat(updated.get("description").asText()).isEqualTo("now described");

        assertThat(apiGet(owner, base, 200).get("totalElements").asInt()).isEqualTo(1);
        assertThat(apiGet(owner, base + "/" + id, 200).get("id").asText()).isEqualTo(id);
    }

    @Test
    @DisplayName("soft-deletes (ADMIN); a MEMBER cannot delete")
    void softDeleteAndPermissions() throws Exception {
        String id = createProfile(owner, orgId, projectId, "Doomed");
        String member = addMember(owner, orgId, "MEMBER");
        apiDelete(member, base + "/" + id, 403);
        apiDelete(owner, base + "/" + id, 204);
        apiGet(owner, base + "/" + id, 404);
    }

    @Test
    @DisplayName("enforces tenant isolation and auth")
    void isolationAndAuth() throws Exception {
        String otherProject = createProject(owner, orgId, "Other Profile Project");
        String foreign = createProfile(owner, orgId, otherProject, "Theirs");
        apiGet(owner, base + "/" + foreign, 404);

        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        apiGet(stranger, base, 403);
        apiGet(null, base, 401);
    }

    // ------------------------------------------------------------------
    // Versioning (Milestone 1) — mirrors Dataset/Prompt: create makes version 1, editing the
    // scoring config (metrics/passThreshold) creates a new version, plain metadata edits don't.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a new profile starts at version 1")
    void createStartsAtVersionOne() throws Exception {
        JsonNode p = apiPost(owner, base, Map.of("name", "Versioned"), 201);
        assertThat(p.get("currentVersionNumber").asInt()).isEqualTo(1);
        assertThat(p.get("enabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("changing metrics creates a new version; a metadata-only edit does not")
    void metricsEditCreatesVersionMetadataEditDoesNot() throws Exception {
        JsonNode created = apiPost(owner, base, body("name", "Evolving",
                "metrics", List.of(Map.of("type", "EXACT_MATCH"))), 201);
        String id = created.get("id").asText();
        assertThat(created.get("currentVersionNumber").asInt()).isEqualTo(1);

        JsonNode afterMetadataEdit = apiPatch(owner, base + "/" + id,
                Map.of("description", "just a description change"), 200);
        assertThat(afterMetadataEdit.get("currentVersionNumber").asInt()).isEqualTo(1);

        JsonNode afterMetricsEdit = apiPatch(owner, base + "/" + id,
                Map.of("metrics", List.of(Map.of("type", "NON_EMPTY"))), 200);
        assertThat(afterMetricsEdit.get("currentVersionNumber").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("submitting the same metrics again does not create a redundant version")
    void resubmittingIdenticalMetricsSkipsVersionBump() throws Exception {
        JsonNode created = apiPost(owner, base, body("name", "Stable",
                "metrics", List.of(Map.of("type", "EXACT_MATCH"))), 201);
        String id = created.get("id").asText();

        JsonNode afterNoOpEdit = apiPatch(owner, base + "/" + id,
                body("metrics", List.of(Map.of("type", "EXACT_MATCH"))), 200);
        assertThat(afterNoOpEdit.get("currentVersionNumber").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("duplicate creates an independent copy seeded from the source's current version")
    void duplicateCopiesCurrentVersion() throws Exception {
        JsonNode source = apiPost(owner, base, body("name", "Original",
                "metrics", List.of(Map.of("type", "EXACT_MATCH")), "passThreshold", 0.8), 201);
        String sourceId = source.get("id").asText();

        JsonNode copy = apiPost(owner, base + "/" + sourceId + "/duplicate", Map.of(), 201);
        assertThat(copy.get("id").asText()).isNotEqualTo(sourceId);
        assertThat(copy.get("name").asText()).isEqualTo("Original (copy)");
        assertThat(copy.get("metrics")).hasSize(1);
        assertThat(copy.get("passThreshold").asDouble()).isEqualTo(0.8);
        assertThat(copy.get("currentVersionNumber").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("enable/disable toggles the profile's enabled flag")
    void enableDisableToggles() throws Exception {
        String id = createProfile(owner, orgId, projectId, "Togglable");
        JsonNode disabled = apiPost(owner, base + "/" + id + "/disable", Map.of(), 200);
        assertThat(disabled.get("enabled").asBoolean()).isFalse();
        JsonNode enabled = apiPost(owner, base + "/" + id + "/enable", Map.of(), 200);
        assertThat(enabled.get("enabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("list search filters by name/slug, case-insensitively")
    void searchFiltersByNameOrSlug() throws Exception {
        createProfile(owner, orgId, projectId, "Customer Support Conversation");
        createProfile(owner, orgId, projectId, "Internal QA");

        JsonNode results = apiGet(owner, base + "?search=conversation", 200);
        assertThat(results.get("totalElements").asInt()).isEqualTo(1);
        assertThat(results.get("content").get(0).get("name").asText()).isEqualTo("Customer Support Conversation");
    }
}
