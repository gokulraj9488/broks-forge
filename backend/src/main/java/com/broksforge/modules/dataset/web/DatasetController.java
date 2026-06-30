package com.broksforge.modules.dataset.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.dataset.web.dto.CreateDatasetRequest;
import com.broksforge.modules.dataset.web.dto.DatasetFilter;
import com.broksforge.modules.dataset.web.dto.DatasetResponse;
import com.broksforge.modules.dataset.web.dto.DatasetSummaryResponse;
import com.broksforge.modules.dataset.web.dto.UpdateDatasetRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Dataset container CRUD, search and lifecycle. Versions, items and statistics
 * live in {@link DatasetVersionController} under the same path.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/datasets")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Datasets", description = "Manage versioned evaluation datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping
    @Operation(summary = "Create a dataset")
    public ResponseEntity<DatasetResponse> create(@PathVariable UUID organizationId,
                                                  @PathVariable UUID projectId,
                                                  @Valid @RequestBody CreateDatasetRequest request) {
        DatasetResponse response =
                datasetService.create(SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List & search datasets")
    public ResponseEntity<PageResponse<DatasetSummaryResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @ParameterObject DatasetFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(datasetService.search(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, filter, pageable));
    }

    @GetMapping("/{datasetId}")
    @Operation(summary = "Get a dataset")
    public ResponseEntity<DatasetResponse> get(@PathVariable UUID organizationId,
                                               @PathVariable UUID projectId,
                                               @PathVariable UUID datasetId) {
        return ResponseEntity.ok(datasetService.get(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, datasetId));
    }

    @PatchMapping("/{datasetId}")
    @Operation(summary = "Update a dataset")
    public ResponseEntity<DatasetResponse> update(@PathVariable UUID organizationId,
                                                  @PathVariable UUID projectId,
                                                  @PathVariable UUID datasetId,
                                                  @Valid @RequestBody UpdateDatasetRequest request) {
        return ResponseEntity.ok(datasetService.update(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, datasetId, request));
    }

    @DeleteMapping("/{datasetId}")
    @Operation(summary = "Delete a dataset", description = "Soft-deletes the dataset. Requires ADMIN or higher.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID datasetId) {
        datasetService.delete(SecurityUtils.requireCurrentUserId(), organizationId, projectId, datasetId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{datasetId}/archive")
    @Operation(summary = "Archive a dataset")
    public ResponseEntity<DatasetResponse> archive(@PathVariable UUID organizationId,
                                                   @PathVariable UUID projectId,
                                                   @PathVariable UUID datasetId) {
        return ResponseEntity.ok(datasetService.archive(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, datasetId));
    }

    @PostMapping("/{datasetId}/unarchive")
    @Operation(summary = "Unarchive a dataset")
    public ResponseEntity<DatasetResponse> unarchive(@PathVariable UUID organizationId,
                                                     @PathVariable UUID projectId,
                                                     @PathVariable UUID datasetId) {
        return ResponseEntity.ok(datasetService.unarchive(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, datasetId));
    }
}
