package com.broksforge.modules.dataset.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The file-upload path for dataset versions (CSV/JSON/XLSX/ZIP), additive to the pre-existing
 * paste-mode {@code POST .../versions} endpoint. Verifies real-world-scale files (dozens of rows,
 * multiple metadata columns) rather than toy two-row examples, plus checksum-based duplicate
 * detection, unsupported-format rejection, and that paste mode is completely unaffected.
 */
@DisplayName("Dataset file upload (CSV / JSON / XLSX / ZIP)")
class DatasetUploadIntegrationTest extends AbstractIntegrationTest {

    private String owner;
    private String orgId;
    private String projectId;
    private String datasetId;
    private String base;
    private String uploadsUrl;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        orgId = createOrg(owner, "Upload Org");
        projectId = createProject(owner, orgId, "Upload Project");
        base = projectBase(orgId, projectId) + "/datasets";
        datasetId = idOf(apiPost(owner, base, java.util.Map.of("name", "Support Tickets"), 201));
        uploadsUrl = base + "/" + datasetId + "/uploads";
    }

    /** A realistic 30-row customer-support QA export with several metadata columns, not a toy example. */
    private String realWorldCsv() {
        StringBuilder csv = new StringBuilder("input,expected_output,category,difficulty,language\n");
        String[] categories = {"billing", "shipping", "account", "technical", "returns"};
        String[] difficulties = {"easy", "medium", "hard"};
        for (int i = 1; i <= 30; i++) {
            csv.append("\"How do I resolve support ticket #").append(i).append("?\",")
                    .append("\"Follow runbook step ").append(i % 7 + 1).append("\",")
                    .append(categories[i % categories.length]).append(',')
                    .append(difficulties[i % difficulties.length]).append(',')
                    .append(i % 3 == 0 ? "es" : "en").append('\n');
        }
        return csv.toString();
    }

    private byte[] realWorldXlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("tickets");
            Row header = sheet.createRow(0);
            String[] columns = {"input", "expected_output", "category", "priority"};
            for (int c = 0; c < columns.length; c++) {
                header.createCell(c).setCellValue(columns[c]);
            }
            for (int r = 1; r <= 40; r++) {
                Row row = sheet.createRow(r);
                row.createCell(0).setCellValue("Ticket " + r + ": customer cannot reset password");
                row.createCell(1).setCellValue("Send reset link and confirm receipt");
                row.createCell(2).setCellValue(r % 2 == 0 ? "account" : "technical");
                row.createCell(3).setCellValue(r); // numeric cell — exercises DataFormatter
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] zipOf(String entryName, byte[] content) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
            zos.finish();
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("uploads a real-world CSV file and creates a version with all rows")
    void uploadsCsvFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "support-tickets.csv", "text/csv",
                realWorldCsv().getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart(uploadsUrl).file(file)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode upload = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(upload.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(upload.get("format").asText()).isEqualTo("CSV");
        assertThat(upload.get("rowCount").asInt()).isEqualTo(30);
        assertThat(upload.get("columnCount").asInt()).isEqualTo(5);
        assertThat(upload.get("checksum").asText()).isNotBlank();
        assertThat(upload.get("datasetVersionId").asText()).isNotBlank();

        JsonNode dataset = apiGet(owner, base + "/" + datasetId, 200);
        assertThat(dataset.get("currentItemCount").asInt()).isEqualTo(30);
        assertThat(dataset.get("latestVersionNumber").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("uploads a real-world XLSX workbook and parses numeric cells via DataFormatter")
    void uploadsXlsxFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "tickets.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", realWorldXlsx());

        JsonNode upload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(file)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(upload.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(upload.get("format").asText()).isEqualTo("XLSX");
        assertThat(upload.get("rowCount").asInt()).isEqualTo(40);
        assertThat(upload.get("columnCount").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("uploads a ZIP archive, unwraps the CSV inside, and records the version as CSV")
    void uploadsZipArchive() throws Exception {
        byte[] zip = zipOf("data/support-tickets.csv", realWorldCsv().getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("file", "export.zip", "application/zip", zip);

        JsonNode upload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(file)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(upload.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(upload.get("rowCount").asInt()).isEqualTo(30);

        JsonNode version = apiGet(owner, base + "/" + datasetId + "/versions/"
                + upload.get("datasetVersionId").asText(), 200);
        assertThat(version.get("sourceFormat").asText()).isEqualTo("CSV"); // unwrapped, not ZIP
    }

    @Test
    @DisplayName("uploads a JSON array file")
    void uploadsJsonFile() throws Exception {
        String json = "[{\"input\":\"q1\",\"expected_output\":\"a1\"},{\"input\":\"q2\",\"expected_output\":\"a2\"}]";
        MockMultipartFile file = new MockMultipartFile("file", "pairs.json", "application/json",
                json.getBytes(StandardCharsets.UTF_8));

        JsonNode upload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(file)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(upload.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(upload.get("rowCount").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("detects duplicate content by checksum without creating a second version")
    void detectsDuplicateContent() throws Exception {
        byte[] content = realWorldCsv().getBytes(StandardCharsets.UTF_8);
        MockMultipartFile first = new MockMultipartFile("file", "a.csv", "text/csv", content);
        MockMultipartFile second = new MockMultipartFile("file", "a-copy.csv", "text/csv", content);

        JsonNode firstUpload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(first)
                        .header("Authorization", "Bearer " + owner))
                .andReturn().getResponse().getContentAsString());

        JsonNode secondUpload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(second)
                        .header("Authorization", "Bearer " + owner))
                .andReturn().getResponse().getContentAsString());

        assertThat(secondUpload.get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(secondUpload.get("datasetVersionId").asText())
                .isEqualTo(firstUpload.get("datasetVersionId").asText());

        // Only one version was actually created.
        JsonNode dataset = apiGet(owner, base + "/" + datasetId, 200);
        assertThat(dataset.get("latestVersionNumber").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects an unsupported file extension with a FAILED-free 400, before any parsing")
    void rejectsUnsupportedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart(uploadsUrl).file(file).header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("records a FAILED upload with an error message for malformed content, without touching the dataset")
    void recordsFailedUploadForMalformedContent() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "broken.json", "application/json",
                "not valid json".getBytes(StandardCharsets.UTF_8));

        JsonNode upload = objectMapper.readTree(mockMvc.perform(multipart(uploadsUrl).file(file)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(upload.get("status").asText()).isEqualTo("FAILED");
        assertThat(upload.get("errorMessage").asText()).isNotBlank();
        assertThat(upload.get("datasetVersionId")).isNull(); // omitted: null fields aren't serialized

        // The dataset itself is untouched by the failed attempt.
        JsonNode dataset = apiGet(owner, base + "/" + datasetId, 200);
        assertThat(dataset.get("latestVersionNumber").asInt()).isZero();
    }

    @Test
    @DisplayName("lists upload history newest-first")
    void listsUploadHistory() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "a.csv", "text/csv",
                realWorldCsv().getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart(uploadsUrl).file(csv).header("Authorization", "Bearer " + owner));
        mockMvc.perform(multipart(uploadsUrl).file(csv).header("Authorization", "Bearer " + owner)); // duplicate

        JsonNode page = apiGet(owner, uploadsUrl, 200);
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("status").asText()).isEqualTo("DUPLICATE");
    }

    @Test
    @DisplayName("paste mode is unaffected: existing inline CSV import still works and writes no upload row")
    void pasteModeUnaffected() throws Exception {
        JsonNode version = apiPost(owner, base + "/" + datasetId + "/versions",
                java.util.Map.of("format", "CSV", "content", "input,expected_output\nhello,hi\n"), 201);
        assertThat(version.get("itemCount").asInt()).isEqualTo(1);

        // The paste-mode import produced a version, but no DatasetUpload row.
        JsonNode uploads = apiGet(owner, uploadsUrl, 200);
        assertThat(uploads.get("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("a MEMBER can upload but a non-member cannot (tenant isolation)")
    void isolationAndAuth() throws Exception {
        String stranger = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        MockMultipartFile file = new MockMultipartFile("file", "a.csv", "text/csv",
                realWorldCsv().getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart(uploadsUrl).file(file).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
    }
}
