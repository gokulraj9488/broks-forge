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
import com.broksforge.modules.dataset.domain.DatasetVersion;
import com.broksforge.modules.dataset.repository.DatasetItemRepository;
import com.broksforge.modules.dataset.repository.DatasetRepository;
import com.broksforge.modules.dataset.repository.DatasetSpecifications;
import com.broksforge.modules.dataset.repository.DatasetVersionRepository;
import com.broksforge.modules.dataset.repository.DatasetVersionStats;
import com.broksforge.modules.dataset.web.DatasetMapper;
import com.broksforge.modules.dataset.web.dto.CreateDatasetRequest;
import com.broksforge.modules.dataset.web.dto.DatasetFilter;
import com.broksforge.modules.dataset.web.dto.DatasetItemResponse;
import com.broksforge.modules.dataset.web.dto.DatasetResponse;
import com.broksforge.modules.dataset.web.dto.DatasetStatsResponse;
import com.broksforge.modules.dataset.web.dto.DatasetSummaryResponse;
import com.broksforge.modules.dataset.web.dto.DatasetVersionResponse;
import com.broksforge.modules.dataset.web.dto.ImportDatasetRequest;
import com.broksforge.modules.dataset.web.dto.UpdateDatasetRequest;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

/**
 * Application service for the dataset aggregate: container CRUD plus immutable,
 * versioned imports from CSV/JSON. New rows never mutate an existing version — an
 * import always produces a fresh {@link DatasetVersion} (see ADR 0007), keeping
 * every evaluation reproducible.
 */
@Slf4j
@Service
public class DatasetService {

    /** Defensive upper bound on rows per import to bound memory and write amplification. */
    private static final int MAX_ITEMS_PER_IMPORT = 50_000;
    private static final List<String> DEFAULT_INPUT_KEYS = List.of("input", "prompt", "question", "text");
    private static final List<String> DEFAULT_EXPECTED_KEYS =
            List.of("expected_output", "expected", "output", "answer", "reference", "target");

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository versionRepository;
    private final DatasetItemRepository itemRepository;
    private final DatasetAccessGuard accessGuard;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final DatasetMapper mapper;
    private final ObjectMapper objectMapper;

    public DatasetService(DatasetRepository datasetRepository,
                          DatasetVersionRepository versionRepository,
                          DatasetItemRepository itemRepository,
                          DatasetAccessGuard accessGuard,
                          OrganizationAccessService accessService,
                          ProjectService projectService,
                          DatasetMapper mapper,
                          ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
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
        if (parsed.items().isEmpty()) {
            throw new BadRequestException(ErrorCode.DATASET_EMPTY, "The import produced no rows");
        }
        if (parsed.items().size() > MAX_ITEMS_PER_IMPORT) {
            throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED,
                    "Imports are limited to %d rows".formatted(MAX_ITEMS_PER_IMPORT));
        }

        int nextNumber = dataset.getLatestVersionNumber() + 1;
        DatasetVersion version = new DatasetVersion();
        version.setDatasetId(datasetId);
        version.setOrganizationId(organizationId);
        version.setProjectId(projectId);
        version.setVersionNumber(nextNumber);
        version.setDescription(trimToNull(request.description()));
        version.setSourceFormat(request.format());
        version.setItemCount(parsed.items().size());
        version.setColumns(new ArrayList<>(parsed.columns()));
        version.setChecksum(sha256Hex(request.content()));
        DatasetVersion savedVersion = versionRepository.save(version);

        List<DatasetItem> items = new ArrayList<>(parsed.items().size());
        int sequence = 0;
        for (ParsedItem parsedItem : parsed.items()) {
            DatasetItem item = new DatasetItem();
            item.setDatasetVersionId(savedVersion.getId());
            item.setDatasetId(datasetId);
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

        log.info("Dataset {} imported version {} with {} rows ({}) by {}",
                datasetId, nextNumber, items.size(), request.format(), actorId);
        return mapper.toVersionResponse(savedVersion);
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

    // ----------------------------------------------------------------------
    // Parsing
    // ----------------------------------------------------------------------

    private ParsedImport parse(ImportDatasetRequest request) {
        try {
            return switch (request.format()) {
                case CSV -> parseCsv(request);
                case JSON, MANUAL -> parseJson(request);
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
        List<List<String>> rows = Csv.parse(request.content());
        if (rows.size() < 2) {
            throw new BadRequestException(ErrorCode.DATASET_EMPTY,
                    "CSV must contain a header row and at least one data row");
        }
        List<String> header = rows.get(0).stream().map(String::trim).toList();
        int inputIdx = resolveColumnIndex(header, request.inputField(), DEFAULT_INPUT_KEYS, true);
        int expectedIdx = resolveColumnIndex(header, request.expectedOutputField(), DEFAULT_EXPECTED_KEYS, false);

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
        List<Map<String, Object>> rows =
                objectMapper.readValue(request.content(), new TypeReference<List<Map<String, Object>>>() {
                });
        if (rows == null || rows.isEmpty()) {
            throw new BadRequestException(ErrorCode.DATASET_EMPTY, "JSON array contained no rows");
        }
        String inputKey = request.inputField() != null
                ? request.inputField() : firstPresentKey(rows, DEFAULT_INPUT_KEYS);
        if (inputKey == null) {
            throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED,
                    "Could not determine an input field; specify inputField");
        }
        String expectedKey = request.expectedOutputField() != null
                ? request.expectedOutputField() : firstPresentKey(rows, DEFAULT_EXPECTED_KEYS);

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

    private int resolveColumnIndex(List<String> header, String requested, List<String> defaults, boolean required) {
        if (StringUtils.hasText(requested)) {
            int idx = indexOfIgnoreCase(header, requested.trim());
            if (idx < 0 && required) {
                throw new BadRequestException(ErrorCode.DATASET_IMPORT_FAILED,
                        "Column '%s' was not found in the CSV header".formatted(requested));
            }
            return idx;
        }
        for (String candidate : defaults) {
            int idx = indexOfIgnoreCase(header, candidate);
            if (idx >= 0) {
                return idx;
            }
        }
        return required ? 0 : -1;
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
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
