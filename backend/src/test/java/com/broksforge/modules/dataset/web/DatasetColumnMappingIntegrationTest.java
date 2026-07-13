package com.broksforge.modules.dataset.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for the Bitext-style column-mapping bug: a CSV whose real input/expected-output
 * columns are not literally named {@code input}/{@code expected_output} (e.g. Bitext Customer Support's
 * {@code instruction}/{@code response}) used to import silently and incorrectly — column 0 ("flags")
 * became the input and expected output was always empty, with no error of any kind. Covers the
 * synonym-based auto-mapping, the preview/ambiguity-detection endpoint, the loud failure when no
 * confident mapping exists (replacing the old silent column-0 fallback), and metadata column selection.
 */
@DisplayName("Dataset column mapping (auto-detection, preview, ambiguity)")
class DatasetColumnMappingIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String datasetId;
    private String base;
    private String uploadsUrl;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Mapping Org");
        projectId = createProject(owner, orgId, "Mapping Project");
        base = projectBase(orgId, projectId) + "/datasets";
        datasetId = idOf(apiPost(owner, base, Map.of("name", "Bitext Support"), 201));
        uploadsUrl = base + "/" + datasetId + "/uploads";
    }

    /** A slice of the real Bitext Customer Support Training Dataset's actual column layout and content. */
    private String bitextCsv(int rows) {
        StringBuilder csv = new StringBuilder("flags,instruction,category,intent,response\n");
        String[] flagCodes = {"B", "BQZ", "BLQZ", "BIP", "BCLZ"};
        for (int i = 1; i <= rows; i++) {
            csv.append(flagCodes[i % flagCodes.length]).append(',')
                    .append("\"I do not know how to cancel order #").append(i).append("\",")
                    .append("ORDER,cancel_order,")
                    .append("\"We understand your concern, here is how to cancel order #").append(i).append("\"")
                    .append('\n');
        }
        return csv.toString();
    }

    @Test
    @DisplayName("root-cause regression: Bitext-style instruction/response columns are auto-detected correctly")
    void bitextColumnsAutoDetectedCorrectly() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bitext.csv", "text/csv",
                bitextCsv(50).getBytes(StandardCharsets.UTF_8));

        JsonNode upload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(file)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(upload.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(upload.get("rowCount").asInt()).isEqualTo(50);
        String versionId = upload.get("datasetVersionId").asText();

        // Items page: input must be the actual customer question, never a flag code.
        JsonNode items = apiGet(owner, base + "/" + datasetId + "/versions/" + versionId + "/items?size=5", 200);
        JsonNode first = items.get("content").get(0);
        assertThat(first.get("input").asText()).contains("I do not know how to cancel order");
        assertThat(first.get("input").asText()).doesNotStartWith("B"); // not a bare flag code like "BQZ"
        assertThat(first.get("expectedOutput").asText()).contains("here is how to cancel order");
        assertThat(first.get("metadata").get("category").asText()).isEqualTo("ORDER");
        assertThat(first.get("metadata").get("intent").asText()).isEqualTo("cancel_order");
        assertThat(first.get("metadata").has("flags")).isTrue();

        // Statistics: full expected-output coverage, not the 0-coverage/3-char-average symptom.
        JsonNode stats = apiGet(owner, base + "/" + datasetId + "/stats?versionId=" + versionId, 200);
        assertThat(stats.get("itemCount").asLong()).isEqualTo(50);
        assertThat(stats.get("itemsWithExpectedOutput").asLong()).isEqualTo(50);
        assertThat(stats.get("expectedOutputCoverage").asDouble()).isEqualTo(100.0);
        assertThat(stats.get("avgInputLength").asDouble()).isGreaterThan(20.0); // real sentences, not "B"/"BQZ"
    }

    @Test
    @DisplayName("preview reports an unambiguous, confident mapping for Bitext columns (no dialog needed)")
    void previewIsUnambiguousForBitext() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bitext.csv", "text/csv",
                bitextCsv(10).getBytes(StandardCharsets.UTF_8));

        JsonNode preview = objectMapper.readTree(mockMvc.perform(
                        multipart(uploadsUrl + "/preview").file(file).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(preview.get("ambiguous").asBoolean()).isFalse();
        assertThat(preview.get("suggestedInputField").asText()).isEqualTo("instruction");
        assertThat(preview.get("suggestedExpectedOutputField").asText()).isEqualTo("response");
        assertThat(preview.get("columns").toString())
                .contains("flags").contains("instruction").contains("category").contains("intent").contains("response");
        assertThat(preview.get("previewRows")).hasSize(5); // capped at 5 even though the file has 10 rows
        assertThat(preview.get("totalRows").asInt()).isEqualTo(10);
        // Nothing was persisted by the preview.
        JsonNode dataset = apiGet(owner, base + "/" + datasetId, 200);
        assertThat(dataset.get("latestVersionNumber").asInt()).isZero();
    }

    @Test
    @DisplayName("preview flags ambiguity when multiple columns could be the input")
    void previewFlagsAmbiguousMultipleInputCandidates() throws Exception {
        String csv = "question,prompt,answer\nWhat time is it?,What time is it?,It is noon\n";
        MockMultipartFile file = new MockMultipartFile("file", "ambiguous.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        JsonNode preview = objectMapper.readTree(mockMvc.perform(
                        multipart(uploadsUrl + "/preview").file(file).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(preview.get("ambiguous").asBoolean()).isTrue();
        assertThat(preview.get("inputCandidates")).hasSize(2);
    }

    @Test
    @DisplayName("preview flags ambiguity when no column matches any known input synonym")
    void previewFlagsAmbiguousNoInputCandidate() throws Exception {
        String csv = "colA,colB\nfoo,bar\n";
        MockMultipartFile file = new MockMultipartFile("file", "unknown.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        JsonNode preview = objectMapper.readTree(mockMvc.perform(
                        multipart(uploadsUrl + "/preview").file(file).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(preview.get("ambiguous").asBoolean()).isTrue();
        assertThat(preview.get("suggestedInputField")).isNull(); // omitted: null fields aren't serialized
        assertThat(preview.get("inputCandidates")).isEmpty();
    }

    @Test
    @DisplayName("uploading with no detectable input column fails loudly instead of silently importing the wrong column")
    void uploadFailsLoudlyWhenNoInputColumnDetected() throws Exception {
        String csv = "colA,colB\nfoo,bar\nbaz,qux\n";
        MockMultipartFile file = new MockMultipartFile("file", "unknown.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        JsonNode upload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(file)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(upload.get("status").asText()).isEqualTo("FAILED");
        assertThat(upload.get("errorMessage").asText()).containsIgnoringCase("input column");
        // No version was silently created from the wrong column.
        JsonNode dataset = apiGet(owner, base + "/" + datasetId, 200);
        assertThat(dataset.get("latestVersionNumber").asInt()).isZero();
    }

    @Test
    @DisplayName("an ambiguous mapping can be resolved by passing explicit inputField/expectedOutputField")
    void explicitFieldsResolveAmbiguity() throws Exception {
        String csv = "question,prompt,answer\nWhat time is it?,ignored,It is noon\n";
        MockMultipartFile file = new MockMultipartFile("file", "ambiguous.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        JsonNode upload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(file)
                        .param("inputField", "question")
                        .param("expectedOutputField", "answer")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(upload.get("status").asText()).isEqualTo("COMPLETED");
        String versionId = upload.get("datasetVersionId").asText();
        JsonNode items = apiGet(owner, base + "/" + datasetId + "/versions/" + versionId + "/items", 200);
        JsonNode first = items.get("content").get(0);
        assertThat(first.get("input").asText()).isEqualTo("What time is it?");
        assertThat(first.get("expectedOutput").asText()).isEqualTo("It is noon");
        assertThat(first.get("metadata").has("prompt")).isTrue(); // the unused candidate becomes metadata
    }

    @Test
    @DisplayName("metadataFields restricts which extra columns are kept, without changing input/expected output")
    void metadataFieldsRestrictsExtraColumns() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bitext.csv", "text/csv",
                bitextCsv(5).getBytes(StandardCharsets.UTF_8));

        JsonNode upload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(file)
                        .param("metadataFields", "category")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        String versionId = upload.get("datasetVersionId").asText();
        JsonNode items = apiGet(owner, base + "/" + datasetId + "/versions/" + versionId + "/items", 200);
        JsonNode metadata = items.get("content").get(0).get("metadata");
        assertThat(metadata.has("category")).isTrue();
        assertThat(metadata.has("intent")).isFalse();
        assertThat(metadata.has("flags")).isFalse();
    }

    @Test
    @DisplayName("paste mode also benefits from the expanded synonym list (instruction/response)")
    void pasteModeRecognisesExpandedSynonyms() throws Exception {
        String csv = "instruction,response\nHow do I reset my password?,Click forgot password\n";
        JsonNode version = apiPost(owner, base + "/" + datasetId + "/versions",
                Map.of("format", "CSV", "content", csv), 201);
        assertThat(version.get("itemCount").asInt()).isEqualTo(1);

        JsonNode items = apiGet(owner, base + "/" + datasetId + "/versions/" + idOf(version) + "/items", 200);
        JsonNode first = items.get("content").get(0);
        assertThat(first.get("input").asText()).isEqualTo("How do I reset my password?");
        assertThat(first.get("expectedOutput").asText()).isEqualTo("Click forgot password");
    }

    @Test
    @DisplayName("existing input/expected_output datasets are completely unaffected (backward compatibility)")
    void existingInputExpectedOutputStillWorks() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "classic.csv", "text/csv",
                "input,expected_output\nWhat is 2+2?,4\n".getBytes(StandardCharsets.UTF_8));

        JsonNode upload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(file)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(upload.get("status").asText()).isEqualTo("COMPLETED");
        String versionId = upload.get("datasetVersionId").asText();
        JsonNode items = apiGet(owner, base + "/" + datasetId + "/versions/" + versionId + "/items", 200);
        JsonNode first = items.get("content").get(0);
        assertThat(first.get("input").asText()).isEqualTo("What is 2+2?");
        assertThat(first.get("expectedOutput").asText()).isEqualTo("4");
    }
}
