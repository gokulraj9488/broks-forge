package com.broksforge.modules.dataset.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.util.Csv;
import com.broksforge.common.util.SlugGenerator;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.dataset.domain.Dataset;
import com.broksforge.modules.dataset.domain.DatasetItem;
import com.broksforge.modules.dataset.domain.DatasetSourceFormat;
import com.broksforge.modules.dataset.domain.DatasetUpload;
import com.broksforge.modules.dataset.domain.DatasetUploadStatus;
import com.broksforge.modules.dataset.domain.DatasetVersion;
import com.broksforge.modules.dataset.repository.DatasetItemRepository;
import com.broksforge.modules.dataset.repository.DatasetRepository;
import com.broksforge.modules.dataset.repository.DatasetSpecifications;
import com.broksforge.modules.dataset.repository.DatasetUploadRepository;
import com.broksforge.modules.dataset.repository.DatasetVersionRepository;
import com.broksforge.modules.dataset.repository.DatasetVersionStats;
import com.broksforge.modules.dataset.web.DatasetMapper;
import com.broksforge.modules.dataset.web.dto.CreateDatasetRequest;
import com.broksforge.modules.dataset.web.dto.DatasetFilter;
import com.broksforge.modules.dataset.web.dto.DatasetItemResponse;
import com.broksforge.modules.dataset.web.dto.DatasetResponse;
import com.broksforge.modules.dataset.web.dto.DatasetStatsResponse;
import com.broksforge.modules.dataset.web.dto.DatasetSummaryResponse;
import com.broksforge.modules.dataset.web.dto.DatasetUploadPreviewResponse;
import com.broksforge.modules.dataset.web.dto.DatasetUploadResponse;
import com.broksforge.modules.dataset.web.dto.DatasetVersionResponse;
import com.broksforge.modules.dataset.web.dto.ImportDatasetRequest;
import com.broksforge.modules.dataset.web.dto.UpdateDatasetRequest;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Application service for the dataset aggregate: container CRUD plus immutable,
 * versioned imports from CSV/JSON/XLSX/ZIP. New rows never mutate an existing version —
 * an import always produces a fresh {@link DatasetVersion} (see ADR 0007), keeping every
 * evaluation reproducible.
 *
 * <p>Two import paths share the same parsing/version-creation code:</p>
 * <ul>
 *   <li><b>Paste mode</b> ({@link #importVersion}) — the original, unchanged endpoint: an
 *       inline CSV/JSON string body. No {@link DatasetUpload} row is written.</li>
 *   <li><b>File upload</b> ({@link #uploadFile}) — a multipart CSV/JSON/XLSX/ZIP file.
 *       Every attempt is recorded as a {@link DatasetUpload} (filename, checksum, size,
 *       row/column counts, parser status/errors) so the UI can show upload history and
 *       poll a result. Parsing runs synchronously within the request — the same
 *       documented trade-off {@code EvaluationJobExecutor} makes for evaluation jobs — so
 *       the status is already terminal by the time the HTTP response returns; the
 *       persisted status model is what makes moving this behind a real async worker later
 *       a contained change, not an API break.</li>
 * </ul>
 */
@Slf4j
@Service
public class DatasetService {

    /** Defensive upper bound on rows per import to bound memory and write amplification. */
    private static final int MAX_ITEMS_PER_IMPORT = 50_000;
    /** Defensive upper bound on an uploaded file's size (mirrors application.yml's multipart cap). */
    private static final long MAX_UPLOAD_SIZE_BYTES = 25L * 1024 * 1024;
    /**
     * Column-name synonyms tried, in priority order, when {@code inputField}/{@code expectedOutputField}
     * is not supplied explicitly. Deliberately broad — real-world exports (e.g. the Bitext Customer
     * Support dataset's {@code instruction}/{@code response} columns) rarely use the literal names
     * {@code input}/{@code expected_output}. See {@link #resolveColumnIndex} for how "no match" and
     * "more than one match" are handled — neither silently guesses a column.
     */
    private static final List<String> DEFAULT_INPUT_KEYS =
            List.of("input", "instruction", "question", "prompt", "query", "text");
    private static final List<String> DEFAULT_EXPECTED_KEYS =
            List.of("expected_output", "response", "answer", "completion", "output", "reference", "expected",
                    "target");
    /** Upload statuses that already produced (or point at) a usable version, for duplicate detection. */
    private static final List<DatasetUploadStatus> DUPLICATE_CANDIDATE_STATUSES =
            List.of(DatasetUploadStatus.COMPLETED, DatasetUploadStatus.DUPLICATE);

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository versionRepository;
    private final DatasetItemRepository itemRepository;
    private final DatasetUploadRepository uploadRepository;
    private final DatasetAccessGuard accessGuard;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final DatasetMapper mapper;
    private final ObjectMapper objectMapper;

    public DatasetService(DatasetRepository datasetRepository,
                          DatasetVersionRepository versionRepository,
                          DatasetItemRepository itemRepository,
                          DatasetUploadRepository uploadRepository,
                          DatasetAccessGuard accessGuard,
                          OrganizationAccessService accessService,
                          ProjectService projectService,
                          DatasetMapper mapper,
                          ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
        this.uploadRepository = uploadRepository;
        this.accessGuard = accessGuard;
        this.accessService = accessService;
        this.projectService = projectService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DatasetResponse create(UUID actorId, UUID organizationId, UUID projectId, CreateDatasetRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        projectService.assertProjectExists(organizationId, projectId);

        Dataset dataset = new Dataset();
        dataset.setOrganizationId(organizationId);
        dataset.setProjectId(projectId);
        dataset.setOwnerId(actorId);
        dataset.setName(request.name().trim());
        dataset.setSlug(resolveSlug(projectId, request.slug(), request.name()));
        dataset.setDescription(trimToNull(request.description()));
        dataset.setVisibility(request.visibilityOrDefault());
        dataset.setTags(normalizeTags(request.tags()));

        Dataset saved = datasetRepository.save(dataset);
        log.info("Dataset {} ('{}') created in project {} by {}", saved.getId(), saved.getSlug(), projectId, actorId);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<DatasetSummaryResponse> search(UUID actorId, UUID organizationId, UUID projectId,
                                                       DatasetFilter filter, Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        Specification<Dataset> spec = DatasetSpecifications.build(
                projectId, filter.q(), filter.status(), filter.visibility(), filter.tag());
        return PageResponse.from(datasetRepository.findAll(spec, pageable), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public DatasetResponse get(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId) {
        Dataset dataset = accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        return mapper.toResponse(dataset);
    }

    @Transactional
    public DatasetResponse update(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId,
                                  UpdateDatasetRequest request) {
        Dataset dataset = accessGuard.requireManageable(organizationId, projectId, datasetId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(dataset);

        if (StringUtils.hasText(request.name())) {
            dataset.setName(request.name().trim());
        }
        if (request.description() != null) {
            dataset.setDescription(trimToNull(request.description()));
        }
        if (request.visibility() != null) {
            dataset.setVisibility(request.visibility());
        }
        if (request.status() != null) {
            dataset.setStatus(request.status());
        }
        if (request.tags() != null) {
            dataset.setTags(normalizeTags(request.tags()));
        }
        log.info("Dataset {} updated in project {} by {}", datasetId, projectId, actorId);
        return mapper.toResponse(dataset);
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId) {
        Dataset dataset = accessGuard.requireManageable(organizationId, projectId, datasetId, actorId,
                OrganizationRole.ADMIN);
        dataset.softDelete(actorId);
        log.info("Dataset {} soft-deleted in project {} by {}", datasetId, projectId, actorId);
    }

    @Transactional
    public DatasetResponse archive(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId) {
        Dataset dataset = accessGuard.requireManageable(organizationId, projectId, datasetId, actorId,
                OrganizationRole.MEMBER);
        dataset.archive();
        return mapper.toResponse(dataset);
    }

    @Transactional
    public DatasetResponse unarchive(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId) {
        Dataset dataset = accessGuard.requireManageable(organizationId, projectId, datasetId, actorId,
                OrganizationRole.MEMBER);
        dataset.unarchive();
        return mapper.toResponse(dataset);
    }

    // ----------------------------------------------------------------------
    // Versions & items
    // ----------------------------------------------------------------------

    @Transactional
    public DatasetVersionResponse importVersion(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId,
                                                ImportDatasetRequest request) {
        Dataset dataset = accessGuard.requireManageable(organizationId, projectId, datasetId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(dataset);

        ParsedImport parsed = parse(request);
        validateItemCount(parsed);
        DatasetVersion savedVersion = createVersionAndItems(dataset, organizationId, projectId, request.format(),
                trimToNull(request.description()), sha256Hex(request.content()), parsed);

        log.info("Dataset {} imported version {} with {} rows ({}) by {}",
                datasetId, savedVersion.getVersionNumber(), parsed.items().size(), request.format(), actorId);
        return mapper.toVersionResponse(savedVersion);
    }

    // ----------------------------------------------------------------------
    // File upload (CSV / JSON / XLSX / ZIP) — additive to paste-mode import above
    // ----------------------------------------------------------------------

    @Transactional
    public DatasetUploadResponse uploadFile(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId,
                                            MultipartFile file, String description, String inputField,
                                            String expectedOutputField, String metadataFields) {
        Dataset dataset = accessGuard.requireManageable(organizationId, projectId, datasetId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(dataset);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.DATASET_UPLOAD_UNSUPPORTED_FORMAT, "No file was uploaded");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new BadRequestException(ErrorCode.DATASET_UPLOAD_TOO_LARGE,
                    "Uploads are limited to %d MB".formatted(MAX_UPLOAD_SIZE_BYTES / (1024 * 1024)));
        }
        String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload";
        DatasetSourceFormat submittedFormat = detectFormat(filename);

        byte[] bytes = readBytes(file);
        String checksum = sha256Hex(bytes);

        DatasetUpload upload = new DatasetUpload();
        upload.setOrganizationId(organizationId);
        upload.setProjectId(projectId);
        upload.setDatasetId(datasetId);
        upload.setFilename(filename);
        upload.setContentType(file.getContentType());
        upload.setFormat(submittedFormat);
        upload.setSizeBytes(file.getSize());
        upload.setChecksum(checksum);

        // Duplicate detection: identical bytes already produced (or point at) a version for this dataset.
        var duplicate = uploadRepository.findFirstByDatasetIdAndChecksumAndStatusInOrderByCreatedAtDesc(
                datasetId, checksum, DUPLICATE_CANDIDATE_STATUSES);
        if (duplicate.isPresent()) {
            upload.setStatus(DatasetUploadStatus.DUPLICATE);
            upload.setDatasetVersionId(duplicate.get().getDatasetVersionId());
            upload.setRowCount(duplicate.get().getRowCount());
            upload.setColumnCount(duplicate.get().getColumnCount());
            DatasetUpload saved = uploadRepository.save(upload);
            log.info("Dataset {} upload '{}' is a duplicate of an existing version ({})",
                    datasetId, filename, upload.getDatasetVersionId());
            return mapper.toUploadResponse(saved);
        }

        upload.setStatus(DatasetUploadStatus.PARSING);
        DatasetUpload saved = uploadRepository.save(upload);

        try {
            UnwrappedContent content = unwrapIfZip(submittedFormat, bytes);
            ParsedImport parsed = switch (content.format()) {
                case CSV -> fromRowMatrix(Csv.parse(decode(content.bytes())), inputField, expectedOutputField);
                case JSON, MANUAL -> parseJsonContent(decode(content.bytes()), inputField, expectedOutputField);
                case XLSX -> parseXlsx(content.bytes(), inputField, expectedOutputField);
                case ZIP -> throw new BadRequestException(ErrorCode.DATASET_UPLOAD_UNSUPPORTED_FORMAT,
                        "The ZIP archive did not contain a supported CSV/JSON/XLSX entry");
            };
            validateItemCount(parsed);
            parsed = restrictMetadata(parsed, metadataFields);

            DatasetVersion savedVersion = createVersionAndItems(dataset, organizationId, projectId, content.format(),
                    trimToNull(description), checksum, parsed);

            saved.setStatus(DatasetUploadStatus.COMPLETED);
            saved.setRowCount(parsed.items().size());
            saved.setColumnCount(parsed.columns().size());
            saved.setDatasetVersionId(savedVersion.getId());
            log.info("Dataset {} upload '{}' parsed into version {} with {} rows by {}",
                    datasetId, filename, savedVersion.getVersionNumber(), parsed.items().size(), actorId);
        } catch (BadRequestException e) {
            saved.setStatus(DatasetUploadStatus.FAILED);
            saved.setErrorMessage(e.getMessage());
            log.info("Dataset {} upload '{}' failed to parse: {}", datasetId, filename, e.getMessage());
        } catch (Exception e) {
            saved.setStatus(DatasetUploadStatus.FAILED);
            saved.setErrorMessage("Could not parse the uploaded file");
            log.warn("Dataset {} upload '{}' failed unexpectedly", datasetId, filename, e);
        }
        return mapper.toUploadResponse(saved);
    }

    @Transactional(readOnly = true)
    public DatasetUploadResponse getUpload(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId,
                                           UUID uploadId) {
        accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        return mapper.toUploadResponse(getUploadOrThrow(datasetId, uploadId));
    }

    @Transactional(readOnly = true)
    public PageResponse<DatasetUploadResponse> listUploads(UUID actorId, UUID organizationId, UUID projectId,
                                                           UUID datasetId, Pageable pageable) {
        accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        return PageResponse.from(
                uploadRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId, pageable),
                mapper::toUploadResponse);
    }

    /**
     * Dry-run over an uploaded file: detects columns and suggests an input/expected-output mapping
     * without creating a version or a {@link DatasetUpload} row. The frontend calls this first; when
     * the mapping is unambiguous it can go straight to {@link #uploadFile}, and when it is not, it
     * shows a mapping dialog (pre-filled with this response) and calls {@link #uploadFile} with the
     * user's confirmed {@code inputField}/{@code expectedOutputField} afterwards.
     */
    @Transactional(readOnly = true)
    public DatasetUploadPreviewResponse previewUpload(UUID actorId, UUID organizationId, UUID projectId,
                                                       UUID datasetId, MultipartFile file) {
        Dataset dataset = accessGuard.requireManageable(organizationId, projectId, datasetId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(dataset);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.DATASET_UPLOAD_UNSUPPORTED_FORMAT, "No file was uploaded");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new BadRequestException(ErrorCode.DATASET_UPLOAD_TOO_LARGE,
                    "Uploads are limited to %d MB".formatted(MAX_UPLOAD_SIZE_BYTES / (1024 * 1024)));
        }
        String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload";
        DatasetSourceFormat submittedFormat = detectFormat(filename);
        byte[] bytes = readBytes(file);

        RawTable table;
        try {
            UnwrappedContent content = unwrapIfZip(submittedFormat, bytes);
            table = switch (content.format()) {
                case CSV -> rawTableFromRowMatrix(Csv.parse(decode(content.bytes())));
                case JSON, MANUAL -> rawTableFromJson(decode(content.bytes()));
                case XLSX -> rawTableFromRowMatrix(sheetToRowMatrix(content.bytes()));
                case ZIP -> throw new BadRequestException(ErrorCode.DATASET_UPLOAD_UNSUPPORTED_FORMAT,
                        "The ZIP archive did not contain a supported CSV/JSON/XLSX entry");
            };
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Dataset upload preview parse failed: {}", e.getMessage());
            throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED, "Could not parse the uploaded file");
        }
        if (table.columns().isEmpty()) {
            throw new BadRequestException(ErrorCode.DATASET_EMPTY, "The file has no rows to preview");
        }

        List<String> inputCandidates = matchingColumns(table.columns(), DEFAULT_INPUT_KEYS);
        List<String> expectedCandidates = matchingColumns(table.columns(), DEFAULT_EXPECTED_KEYS);
        boolean ambiguous = inputCandidates.size() != 1 || expectedCandidates.size() > 1;

        return new DatasetUploadPreviewResponse(
                table.columns(),
                suggestColumn(table.columns(), DEFAULT_INPUT_KEYS),
                suggestColumn(table.columns(), DEFAULT_EXPECTED_KEYS),
                inputCandidates,
                expectedCandidates,
                ambiguous,
                table.rows().stream().limit(5).toList(),
                table.rows().size());
    }

    private RawTable rawTableFromRowMatrix(List<List<String>> rows) {
        if (rows.isEmpty()) {
            return new RawTable(List.of(), List.of());
        }
        List<String> header = rows.get(0).stream().map(String::trim).toList();
        List<Map<String, String>> dataRows = new ArrayList<>(Math.max(0, rows.size() - 1));
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                rowMap.put(header.get(c), cell(row, c));
            }
            dataRows.add(rowMap);
        }
        return new RawTable(header, dataRows);
    }

    private RawTable rawTableFromJson(String content) throws IOException {
        List<Map<String, Object>> rows =
                objectMapper.readValue(content, new TypeReference<List<Map<String, Object>>>() {
                });
        if (rows == null || rows.isEmpty()) {
            return new RawTable(List.of(), List.of());
        }
        Set<String> columns = new LinkedHashSet<>();
        List<Map<String, String>> dataRows = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            columns.addAll(row.keySet());
            Map<String, String> rowMap = new LinkedHashMap<>();
            row.forEach((key, value) -> rowMap.put(key, stringify(value)));
            dataRows.add(rowMap);
        }
        return new RawTable(new ArrayList<>(columns), dataRows);
    }

    /** A file's columns and rows, decoupled from which column is "input"/"expected output". */
    private record RawTable(List<String> columns, List<Map<String, String>> rows) {
    }

    /**
     * Restricts each item's metadata to {@code allowedColumnsCsv} (comma-separated column names) when
     * provided; {@code null}/blank leaves every non-input/non-expected column as metadata, unchanged
     * from the original behaviour. Additive — existing callers that never pass this see no difference.
     */
    private ParsedImport restrictMetadata(ParsedImport parsed, String allowedColumnsCsv) {
        if (!StringUtils.hasText(allowedColumnsCsv)) {
            return parsed;
        }
        Set<String> allowed = new java.util.HashSet<>();
        for (String column : allowedColumnsCsv.split(",")) {
            if (StringUtils.hasText(column)) {
                allowed.add(column.trim());
            }
        }
        List<ParsedItem> restricted = parsed.items().stream()
                .map(item -> new ParsedItem(item.input(), item.expectedOutput(),
                        filterKeys(item.metadata(), allowed)))
                .toList();
        return new ParsedImport(parsed.columns(), restricted);
    }

    private Map<String, Object> filterKeys(Map<String, Object> source, Set<String> allowedKeys) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (allowedKeys.contains(key)) {
                filtered.put(key, value);
            }
        });
        return filtered;
    }

    private DatasetUpload getUploadOrThrow(UUID datasetId, UUID uploadId) {
        return uploadRepository.findByIdAndDatasetId(uploadId, datasetId)
                .orElseThrow(() -> ResourceNotFoundException.of("Dataset upload", uploadId));
    }

    /**
     * Creates the immutable {@link DatasetVersion} + its {@link DatasetItem} rows and repoints the
     * dataset's current-version pointer, shared by both the paste-mode and file-upload import paths.
     */
    private DatasetVersion createVersionAndItems(Dataset dataset, UUID organizationId, UUID projectId,
                                                 DatasetSourceFormat format, String description, String checksum,
                                                 ParsedImport parsed) {
        int nextNumber = dataset.getLatestVersionNumber() + 1;
        DatasetVersion version = new DatasetVersion();
        version.setDatasetId(dataset.getId());
        version.setOrganizationId(organizationId);
        version.setProjectId(projectId);
        version.setVersionNumber(nextNumber);
        version.setDescription(description);
        version.setSourceFormat(format);
        version.setItemCount(parsed.items().size());
        version.setColumns(new ArrayList<>(parsed.columns()));
        version.setChecksum(checksum);
        DatasetVersion savedVersion = versionRepository.save(version);

        List<DatasetItem> items = new ArrayList<>(parsed.items().size());
        int sequence = 0;
        for (ParsedItem parsedItem : parsed.items()) {
            DatasetItem item = new DatasetItem();
            item.setDatasetVersionId(savedVersion.getId());
            item.setDatasetId(dataset.getId());
            item.setOrganizationId(organizationId);
            item.setSequence(sequence++);
            item.setInput(parsedItem.input());
            item.setExpectedOutput(parsedItem.expectedOutput());
            item.setMetadata(parsedItem.metadata());
            items.add(item);
        }
        itemRepository.saveAll(items);

        dataset.setLatestVersionNumber(nextNumber);
        dataset.setCurrentVersionId(savedVersion.getId());
        dataset.setCurrentItemCount(parsed.items().size());
        return savedVersion;
    }

    private void validateItemCount(ParsedImport parsed) {
        if (parsed.items().isEmpty()) {
            throw new BadRequestException(ErrorCode.DATASET_EMPTY, "The import produced no rows");
        }
        if (parsed.items().size() > MAX_ITEMS_PER_IMPORT) {
            throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED,
                    "Imports are limited to %d rows".formatted(MAX_ITEMS_PER_IMPORT));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DatasetVersionResponse> listVersions(UUID actorId, UUID organizationId, UUID projectId,
                                                             UUID datasetId, Pageable pageable) {
        accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        return PageResponse.from(
                versionRepository.findByDatasetIdOrderByVersionNumberDesc(datasetId, pageable),
                mapper::toVersionResponse);
    }

    @Transactional(readOnly = true)
    public DatasetVersionResponse getVersion(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId,
                                             UUID versionId) {
        accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        return mapper.toVersionResponse(getVersionOrThrow(datasetId, versionId));
    }

    @Transactional(readOnly = true)
    public PageResponse<DatasetItemResponse> listItems(UUID actorId, UUID organizationId, UUID projectId,
                                                       UUID datasetId, UUID versionId, Pageable pageable) {
        accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        getVersionOrThrow(datasetId, versionId);
        return PageResponse.from(
                itemRepository.findByDatasetVersionIdOrderBySequenceAsc(versionId, pageable),
                mapper::toItemResponse);
    }

    @Transactional(readOnly = true)
    public DatasetStatsResponse getStats(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId,
                                         UUID versionId) {
        Dataset dataset = accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        UUID effectiveVersionId = versionId != null ? versionId : dataset.getCurrentVersionId();
        if (effectiveVersionId == null) {
            throw new BadRequestException(ErrorCode.DATASET_VERSION_REQUIRED,
                    "This dataset has no versions yet; import data first");
        }
        DatasetVersion version = getVersionOrThrow(datasetId, effectiveVersionId);
        DatasetVersionStats stats = itemRepository.computeStats(version.getId());
        long itemCount = stats.itemCount();
        long withExpected = stats.withExpectedOutput();
        double coverage = itemCount == 0 ? 0.0 : round((double) withExpected / itemCount * 100.0);
        return new DatasetStatsResponse(
                datasetId,
                version.getId(),
                version.getVersionNumber(),
                itemCount,
                withExpected,
                coverage,
                round(stats.avgInputLength()),
                round(stats.avgExpectedOutputLength()),
                version.getColumns());
    }

    // ----------------------------------------------------------------------
    // Published execution API (consumed by the evaluation/benchmark modules)
    // ----------------------------------------------------------------------

    /**
     * Resolves the dataset version to evaluate against (the supplied version, or the
     * current version) and validates it has rows. Used at job-creation time to pin
     * inputs for reproducibility.
     */
    @Transactional(readOnly = true)
    public DatasetVersionRef resolveVersionForExecution(UUID actorId, UUID organizationId, UUID projectId,
                                                        UUID datasetId, UUID versionId) {
        Dataset dataset = accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        UUID effectiveVersionId = versionId != null ? versionId : dataset.getCurrentVersionId();
        if (effectiveVersionId == null) {
            throw new BadRequestException(ErrorCode.DATASET_VERSION_REQUIRED,
                    "This dataset has no versions yet; import data first");
        }
        DatasetVersion version = getVersionOrThrow(datasetId, effectiveVersionId);
        return new DatasetVersionRef(version.getId(), version.getVersionNumber(), version.getItemCount());
    }

    /**
     * Loads all rows of a dataset version, in sequence order, as id-referenced
     * {@link DatasetRow}s for execution.
     */
    @Transactional(readOnly = true)
    public List<DatasetRow> loadExecutionItems(UUID actorId, UUID organizationId, UUID projectId,
                                               UUID datasetId, UUID versionId) {
        accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        getVersionOrThrow(datasetId, versionId);
        return itemRepository.findByDatasetVersionIdOrderBySequenceAsc(versionId).stream()
                .map(i -> new DatasetRow(i.getId(), i.getSequence(), i.getInput(), i.getExpectedOutput(),
                        i.getMetadata()))
                .toList();
    }

    /**
     * Loads a single dataset row by item id — for read-only, per-run diagnostics (the evaluation
     * module's prompt-render debug view) that already know exactly which item a run used and don't
     * need the whole version loaded. IDOR-safe: the item must belong to {@code datasetId}, not just exist.
     */
    @Transactional(readOnly = true)
    public DatasetRow loadExecutionItem(UUID actorId, UUID organizationId, UUID projectId, UUID datasetId,
                                        UUID itemId) {
        accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        DatasetItem item = itemRepository.findById(itemId)
                .filter(i -> i.getDatasetId().equals(datasetId))
                .orElseThrow(() -> ResourceNotFoundException.of("Dataset item", itemId));
        return new DatasetRow(item.getId(), item.getSequence(), item.getInput(), item.getExpectedOutput(),
                item.getMetadata());
    }

    /**
     * Pages through a dataset version's rows in sequence order, for callers that must stream a
     * large dataset (e.g. the background evaluation runner) instead of loading it entirely into
     * memory. Access is re-checked per page since a caller may hold a page result across a
     * request boundary.
     */
    @Transactional(readOnly = true)
    public Page<DatasetRow> loadExecutionItemsPage(UUID actorId, UUID organizationId, UUID projectId,
                                                    UUID datasetId, UUID versionId, Pageable pageable) {
        accessGuard.requireReadable(organizationId, projectId, datasetId, actorId);
        getVersionOrThrow(datasetId, versionId);
        return itemRepository.findByDatasetVersionIdOrderBySequenceAsc(versionId, pageable)
                .map(i -> new DatasetRow(i.getId(), i.getSequence(), i.getInput(), i.getExpectedOutput(),
                        i.getMetadata()));
    }

    // ----------------------------------------------------------------------
    // Parsing
    // ----------------------------------------------------------------------

    private ParsedImport parse(ImportDatasetRequest request) {
        try {
            return switch (request.format()) {
                case CSV -> parseCsv(request);
                case JSON, MANUAL -> parseJson(request);
                case XLSX, ZIP -> throw new BadRequestException(ErrorCode.DATASET_UPLOAD_UNSUPPORTED_FORMAT,
                        "%s content must be uploaded as a file, not pasted".formatted(request.format()));
            };
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Dataset import parse failed: {}", e.getMessage());
            throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED,
                    "Could not parse the supplied %s content".formatted(request.format()));
        }
    }

    private ParsedImport parseCsv(ImportDatasetRequest request) {
        return fromRowMatrix(Csv.parse(request.content()), request.inputField(), request.expectedOutputField());
    }

    /** Shared by CSV (paste + upload) and XLSX upload — both reduce to a rectangular row matrix. */
    private ParsedImport fromRowMatrix(List<List<String>> rows, String inputField, String expectedOutputField) {
        if (rows.size() < 2) {
            throw new BadRequestException(ErrorCode.DATASET_EMPTY,
                    "The file must contain a header row and at least one data row");
        }
        List<String> header = rows.get(0).stream().map(String::trim).toList();
        int inputIdx = resolveColumnIndex(header, inputField, DEFAULT_INPUT_KEYS, true);
        int expectedIdx = resolveColumnIndex(header, expectedOutputField, DEFAULT_EXPECTED_KEYS, false);

        List<ParsedItem> items = new ArrayList<>(rows.size() - 1);
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String input = cell(row, inputIdx);
            String expected = expectedIdx >= 0 ? cell(row, expectedIdx) : null;
            Map<String, Object> metadata = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                if (c == inputIdx || c == expectedIdx) {
                    continue;
                }
                metadata.put(header.get(c), cell(row, c));
            }
            items.add(new ParsedItem(nullToEmpty(input), blankToNull(expected), metadata));
        }
        return new ParsedImport(header, items);
    }

    private ParsedImport parseJson(ImportDatasetRequest request) throws Exception {
        return parseJsonContent(request.content(), request.inputField(), request.expectedOutputField());
    }

    private ParsedImport parseJsonContent(String content, String inputField, String expectedOutputField)
            throws IOException {
        List<Map<String, Object>> rows =
                objectMapper.readValue(content, new TypeReference<List<Map<String, Object>>>() {
                });
        if (rows == null || rows.isEmpty()) {
            throw new BadRequestException(ErrorCode.DATASET_EMPTY, "JSON array contained no rows");
        }
        String inputKey = inputField != null ? inputField : firstPresentKey(rows, DEFAULT_INPUT_KEYS);
        if (inputKey == null) {
            throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED,
                    "Could not determine an input field; specify inputField");
        }
        String expectedKey = expectedOutputField != null
                ? expectedOutputField : firstPresentKey(rows, DEFAULT_EXPECTED_KEYS);

        Set<String> columns = new LinkedHashSet<>();
        List<ParsedItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            columns.addAll(row.keySet());
            if (!row.containsKey(inputKey)) {
                throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED,
                        "A row is missing the '%s' field".formatted(inputKey));
            }
            String input = stringify(row.get(inputKey));
            String expected = expectedKey != null ? stringify(row.get(expectedKey)) : null;
            Map<String, Object> metadata = new LinkedHashMap<>(row);
            metadata.remove(inputKey);
            if (expectedKey != null) {
                metadata.remove(expectedKey);
            }
            items.add(new ParsedItem(nullToEmpty(input), blankToNull(expected), metadata));
        }
        return new ParsedImport(new ArrayList<>(columns), items);
    }

    // ----------------------------------------------------------------------
    // File-upload format handling (XLSX / ZIP) and shared byte-level helpers
    // ----------------------------------------------------------------------

    private DatasetSourceFormat detectFormat(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return DatasetSourceFormat.CSV;
        }
        if (lower.endsWith(".json")) {
            return DatasetSourceFormat.JSON;
        }
        if (lower.endsWith(".xlsx")) {
            return DatasetSourceFormat.XLSX;
        }
        if (lower.endsWith(".zip")) {
            return DatasetSourceFormat.ZIP;
        }
        throw new BadRequestException(ErrorCode.DATASET_UPLOAD_UNSUPPORTED_FORMAT,
                "Unsupported file type; expected .csv, .json, .xlsx or .zip");
    }

    /** If {@code format} is ZIP, extracts the first supported entry; otherwise returns the input unchanged. */
    private UnwrappedContent unwrapIfZip(DatasetSourceFormat format, byte[] bytes) throws IOException {
        if (format != DatasetSourceFormat.ZIP) {
            return new UnwrappedContent(format, bytes);
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().contains("__MACOSX")) {
                    continue;
                }
                DatasetSourceFormat entryFormat;
                try {
                    entryFormat = detectFormat(entry.getName());
                } catch (BadRequestException ignored) {
                    continue; // not a supported entry — keep scanning the archive
                }
                if (entryFormat == DatasetSourceFormat.ZIP) {
                    continue; // nested zips are not supported
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zis.transferTo(out);
                return new UnwrappedContent(entryFormat, out.toByteArray());
            }
        }
        throw new BadRequestException(ErrorCode.DATASET_UPLOAD_UNSUPPORTED_FORMAT,
                "The ZIP archive did not contain a supported CSV/JSON/XLSX entry");
    }

    /** Reads the first sheet into a CSV-shaped row matrix and delegates to {@link #fromRowMatrix}. */
    private ParsedImport parseXlsx(byte[] bytes, String inputField, String expectedOutputField) throws IOException {
        return fromRowMatrix(sheetToRowMatrix(bytes), inputField, expectedOutputField);
    }

    /** Shared by {@link #parseXlsx} and the preview endpoint — reads the first sheet as a row matrix. */
    private List<List<String>> sheetToRowMatrix(byte[] bytes) throws IOException {
        DataFormatter formatter = new DataFormatter();
        List<List<String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BadRequestException(ErrorCode.DATASET_EMPTY, "The workbook has no sheets");
            }
            Sheet sheet = workbook.getSheetAt(0);
            int lastColumn = 0;
            for (Row row : sheet) {
                lastColumn = Math.max(lastColumn, row.getLastCellNum());
            }
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>(Math.max(lastColumn, 0));
                for (int c = 0; c < lastColumn; c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : formatter.formatCellValue(cell));
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException(ErrorCode.DATASET_UPLOAD_UNSUPPORTED_FORMAT,
                    "Could not read the uploaded file");
        }
    }

    private String decode(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private record UnwrappedContent(DatasetSourceFormat format, byte[] bytes) {
    }

    private int resolveColumnIndex(List<String> header, String requested, List<String> defaults, boolean required) {
        if (StringUtils.hasText(requested)) {
            int idx = indexOfIgnoreCase(header, requested.trim());
            if (idx < 0 && required) {
                throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED,
                        "Column '%s' was not found in the file header".formatted(requested));
            }
            return idx;
        }
        for (String candidate : defaults) {
            int idx = indexOfIgnoreCase(header, candidate);
            if (idx >= 0) {
                return idx;
            }
        }
        // ROOT CAUSE OF THE BITEXT MIS-IMPORT: this used to silently fall back to column 0 for a
        // required field (or -1 for an optional one) when no synonym matched, instead of failing.
        // A CSV whose real input column is named e.g. "instruction" then imported column 0 ("flags")
        // as the input with zero errors — silent data corruption, not a crash. A required field with
        // no confident match must fail loudly; callers that can, preview first (see
        // #matchingColumns/#suggestMapping) and surface a mapping dialog before this is ever reached.
        if (required) {
            throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED,
                    "Could not determine the input column from the header; specify inputField explicitly "
                            + "(no column matched: %s)".formatted(String.join(", ", defaults)));
        }
        return -1;
    }

    /** All header columns (in header order) matching any of {@code synonyms}, case-insensitively. */
    private List<String> matchingColumns(List<String> header, List<String> synonyms) {
        List<String> matches = new ArrayList<>();
        for (String column : header) {
            for (String synonym : synonyms) {
                if (column.equalsIgnoreCase(synonym)) {
                    matches.add(column);
                    break;
                }
            }
        }
        return matches;
    }

    /** The single best-guess column by synonym priority order, or null if none matched. */
    private String suggestColumn(List<String> header, List<String> defaults) {
        for (String candidate : defaults) {
            int idx = indexOfIgnoreCase(header, candidate);
            if (idx >= 0) {
                return header.get(idx);
            }
        }
        return null;
    }

    private String firstPresentKey(List<Map<String, Object>> rows, List<String> candidates) {
        Map<String, Object> first = rows.get(0);
        for (String candidate : candidates) {
            if (first.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private DatasetVersion getVersionOrThrow(UUID datasetId, UUID versionId) {
        return versionRepository.findByIdAndDatasetId(versionId, datasetId)
                .orElseThrow(() -> ResourceNotFoundException.of("Dataset version", versionId));
    }

    private String resolveSlug(UUID projectId, String requestedSlug, String name) {
        if (StringUtils.hasText(requestedSlug)) {
            String slug = requestedSlug.trim().toLowerCase(Locale.ROOT);
            if (datasetRepository.existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(projectId, slug)) {
                throw new ResourceConflictException(ErrorCode.SLUG_ALREADY_EXISTS,
                        "A dataset with this slug already exists in the project");
            }
            return slug;
        }
        return SlugGenerator.uniqueSlug(name,
                candidate -> datasetRepository.existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(projectId, candidate));
    }

    private List<String> normalizeTags(Set<String> requested) {
        if (requested == null) {
            return new ArrayList<>();
        }
        return requested.stream()
                .filter(StringUtils::hasText)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private int indexOfIgnoreCase(List<String> header, String name) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private String cell(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) {
            return "";
        }
        return row.get(idx);
    }

    private String sha256Hex(String content) {
        return sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ParsedImport(List<String> columns, List<ParsedItem> items) {
    }

    private record ParsedItem(String input, String expectedOutput, Map<String, Object> metadata) {
    }
}
