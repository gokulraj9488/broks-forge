package com.broksforge.modules.dataset.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.dataset.web.dto.DatasetItemResponse;
import com.broksforge.modules.dataset.web.dto.DatasetStatsResponse;
import com.broksforge.modules.dataset.web.dto.DatasetVersionResponse;
import com.broksforge.modules.dataset.web.dto.ImportDatasetRequest;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Dataset versions (immutable snapshots), their items, and computed statistics.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/datasets/{datasetId}")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dataset versions", description = "Import and inspect immutable dataset versions")
public class DatasetVersionController {

    private final DatasetService datasetService;

    public DatasetVersionController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping("/versions")
    @Operation(summary = "Import a new dataset version",
            description = "Parses inline CSV or JSON into a new immutable version and makes it the current version.")
    public ResponseEntity<DatasetVersionResponse> importVersion(@PathVariable UUID organizationId,
                                                                @PathVariable UUID projectId,
                                                                @PathVariable UUID datasetId,
                                                                @Valid @RequestBody ImportDatasetRequest request) {
        DatasetVersionResponse response = datasetService.importVersion(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, datasetId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/versions")
    @Operation(summary = "List dataset versions")
    public ResponseEntity<PageResponse<DatasetVersionResponse>> listVersions(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @PathVariable UUID datasetId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(datasetService.listVersions(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, datasetId, pageable));
    }

    @GetMapping("/versions/{versionId}")
    @Operation(summary = "Get a dataset version")
    public ResponseEntity<DatasetVersionResponse> getVersion(@PathVariable UUID organizationId,
                                                             @PathVariable UUID projectId,
                                                             @PathVariable UUID datasetId,
                                                             @PathVariable UUID versionId) {
        return ResponseEntity.ok(datasetService.getVersion(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, datasetId, versionId));
    }

    @GetMapping("/versions/{versionId}/items")
    @Operation(summary = "List the rows of a dataset version")
    public ResponseEntity<PageResponse<DatasetItemResponse>> listItems(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @PathVariable UUID datasetId,
            @PathVariable UUID versionId,
            @ParameterObject @PageableDefault(size = 50, sort = "sequence", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(datasetService.listItems(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, datasetId, versionId, pageable));
    }

    @GetMapping("/stats")
    @Operation(summary = "Dataset statistics",
            description = "Statistics for the given version, or the current version when versionId is omitted.")
    public ResponseEntity<DatasetStatsResponse> stats(@PathVariable UUID organizationId,
                                                      @PathVariable UUID projectId,
                                                      @PathVariable UUID datasetId,
                                                      @RequestParam(required = false) UUID versionId) {
        return ResponseEntity.ok(datasetService.getStats(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, datasetId, versionId));
    }
}
