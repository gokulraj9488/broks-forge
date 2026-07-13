package com.broksforge.modules.dataset.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dataset CRUD plus the immutable, versioned import model (ADR 0007): create/read/update/soft-delete,
 * slug rules, archive/unarchive with the archived-mutation guard, CSV/JSON version import, item
 * listing, statistics, tenant isolation and permissions.
 */
@DisplayName("Dataset CRUD & versioning")
class DatasetCrudIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Data Org");
        projectId = createProject(owner, orgId, "Data Project");
        base = projectBase(orgId, projectId) + "/datasets";
    }

    private static final String CSV = "input,expected_output\nhello,hi\nbye,ciao\n";
    private static final String JSON = "[{\"input\":\"q1\",\"expected_output\":\"a1\"}]";

    @Test
    @DisplayName("creates a dataset with a generated slug and empty version state")
    void createsDataset() throws Exception {
        JsonNode ds = apiPost(owner, base, Map.of("name", "Golden Set"), 201);
        assertThat(ds.get("slug").asText()).isEqualTo("golden-set");
        assertThat(ds.get("visibility").asText()).isEqualTo("PRIVATE");
        assertThat(ds.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(ds.get("latestVersionNumber").asInt()).isZero();
        assertThat(ds.get("currentItemCount").asInt()).isZero();
    }

    @Test
    @DisplayName("honours explicit visibility and auto-suffixes duplicate names")
    void visibilityAndDuplicateName() throws Exception {
        JsonNode org = apiPost(owner, base, Map.of("name", "Shared", "visibility", "ORGANIZATION"), 201);
        assertThat(org.get("visibility").asText()).isEqualTo("ORGANIZATION");

        apiPost(owner, base, Map.of("name", "Dupe"), 201);
        JsonNode second = apiPost(owner, base, Map.of("name", "Dupe"), 201);
        assertThat(second.get("slug").asText()).isEqualTo("dupe-2");
    }

    @Test
    @DisplayName("rejects a duplicate explicit slug (409) and a blank name (400)")
    void rejectsDuplicateSlugAndBlankName() throws Exception {
        apiPost(owner, base, Map.of("name", "First", "slug", "clash"), 201);
        apiPost(owner, base, Map.of("name", "Second", "slug", "clash"), 409);
        apiPost(owner, base, Map.of("name", ""), 400);
    }

    @Test
    @DisplayName("updates metadata and archives via status; an archived dataset rejects imports (409)")
    void updateArchiveGuard() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Editable"), 201));
        JsonNode updated = apiPatch(owner, base + "/" + id, Map.of("description", "described"), 200);
        assertThat(updated.get("description").asText()).isEqualTo("described");

        apiPost(owner, base + "/" + id + "/archive", null, 200);
        apiPost(owner, base + "/" + id + "/versions", Map.of("format", "CSV", "content", CSV), 409);
        apiPost(owner, base + "/" + id + "/unarchive", null, 200);
    }

    @Test
    @DisplayName("soft-deletes (ADMIN) and frees the slug; a MEMBER cannot delete")
    void softDeleteAndPermissions() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Temp", "slug", "temp-ds"), 201));
        String member = addMember(owner, orgId, "MEMBER");
        apiDelete(member, base + "/" + id, 403);
        apiDelete(owner, base + "/" + id, 204);
        apiGet(owner, base + "/" + id, 404);

        JsonNode recreated = apiPost(owner, base, Map.of("name", "Temp2", "slug", "temp-ds"), 201);
        assertThat(recreated.get("slug").asText()).isEqualTo("temp-ds");
    }

    @Test
    @DisplayName("imports a CSV version that becomes current, with parsed items and columns")
    void importsCsvVersion() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Imported"), 201));
        JsonNode version = apiPost(owner, base + "/" + id + "/versions",
                Map.of("format", "CSV", "content", CSV), 201);
        assertThat(version.get("versionNumber").asInt()).isEqualTo(1);
        assertThat(version.get("itemCount").asInt()).isEqualTo(2);
        assertThat(version.get("sourceFormat").asText()).isEqualTo("CSV");
        assertThat(version.get("checksum").asText()).isNotBlank();

        JsonNode ds = apiGet(owner, base + "/" + id, 200);
        assertThat(ds.get("latestVersionNumber").asInt()).isEqualTo(1);
        assertThat(ds.get("currentItemCount").asInt()).isEqualTo(2);
        assertThat(ds.get("currentVersionId").asText()).isEqualTo(idOf(version));
    }

    @Test
    @DisplayName("a second import creates version 2 (immutable, append-only)")
    void secondImportBumpsVersion() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Versioned"), 201));
        apiPost(owner, base + "/" + id + "/versions", Map.of("format", "CSV", "content", CSV), 201);
        JsonNode v2 = apiPost(owner, base + "/" + id + "/versions", Map.of("format", "JSON", "content", JSON), 201);
        assertThat(v2.get("versionNumber").asInt()).isEqualTo(2);
        assertThat(v2.get("itemCount").asInt()).isEqualTo(1);

        JsonNode versions = apiGet(owner, base + "/" + id + "/versions", 200);
        assertThat(versions.get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("lists items and computes statistics for the current version")
    void itemsAndStats() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Stats DS"), 201));
        JsonNode version = apiPost(owner, base + "/" + id + "/versions",
                Map.of("format", "CSV", "content", CSV), 201);

        JsonNode items = apiGet(owner, base + "/" + id + "/versions/" + idOf(version) + "/items", 200);
        assertThat(items.get("totalElements").asInt()).isEqualTo(2);
        assertThat(items.get("content").get(0).get("input").asText()).isEqualTo("hello");

        JsonNode stats = apiGet(owner, base + "/" + id + "/stats", 200);
        assertThat(stats.get("itemCount").asLong()).isEqualTo(2);
        assertThat(stats.get("itemsWithExpectedOutput").asLong()).isEqualTo(2);
        assertThat(stats.get("expectedOutputCoverage").asDouble()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("rejects a blank/empty import with 400")
    void rejectsEmptyImport() throws Exception {
        String id = idOf(apiPost(owner, base, Map.of("name", "Empty Import"), 201));
        apiPost(owner, base + "/" + id + "/versions", Map.of("format", "CSV", "content", ""), 400);
    }

    @Test
    @DisplayName("filters the dataset list")
    void filters() throws Exception {
        apiPost(owner, base, Map.of("name", "Alpha DS"), 201);
        apiPost(owner, base, Map.of("name", "Beta DS"), 201);
        assertThat(apiGet(owner, base, 200).get("totalElements").asInt()).isEqualTo(2);
        assertThat(apiGet(owner, base + "?q=Alpha", 200).get("totalElements").asInt()).isEqualTo(1);
        assertThat(apiGet(owner, base + "?status=ARCHIVED", 200).get("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("enforces tenant isolation and auth")
    void isolationAndAuth() throws Exception {
        String otherProject = createProject(owner, orgId, "Other DS Project");
        String foreign = createDatasetWithItems(owner, orgId, otherProject, "Theirs");
        apiGet(owner, base + "/" + foreign, 404);
        apiGet(owner, base + "/" + UUID.randomUUID(), 404);

        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        apiGet(stranger, base, 403);
        apiGet(null, base, 401);
    }
}
