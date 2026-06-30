package com.broksforge.modules.regression.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.regression.service.RegressionService;
import com.broksforge.modules.regression.web.dto.RegressionDtos.CreateRegressionCheckRequest;
import com.broksforge.modules.regression.web.dto.RegressionDtos.RegressionCheckResponse;
import com.broksforge.modules.regression.web.dto.RegressionDtos.RegressionCheckSummaryResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Regression checks: detect quality/latency/cost/token regressions of a candidate
 * evaluation job against a baseline.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/regression-checks")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Regression checks", description = "Detect regressions between evaluation jobs")
public class RegressionController {

    private final RegressionService regressionService;

    public RegressionController(RegressionService regressionService) {
        this.regressionService = regressionService;
    }

    @PostMapping
    @Operation(summary = "Run a regression check (candidate vs baseline)")
    public ResponseEntity<RegressionCheckResponse> create(@PathVariable UUID organizationId,
                                                          @PathVariable UUID projectId,
                                                          @Valid @RequestBody CreateRegressionCheckRequest request) {
        RegressionCheckResponse response =
                regressionService.create(SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List regression checks")
    public ResponseEntity<PageResponse<RegressionCheckSummaryResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(regressionService.list(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, pageable));
    }

    @GetMapping("/{checkId}")
    @Operation(summary = "Get a regression check with its findings")
    public ResponseEntity<RegressionCheckResponse> get(@PathVariable UUID organizationId,
                                                       @PathVariable UUID projectId,
                                                       @PathVariable UUID checkId) {
        return ResponseEntity.ok(regressionService.get(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, checkId));
    }

    @DeleteMapping("/{checkId}")
    @Operation(summary = "Delete a regression check", description = "Soft-deletes the check. Requires ADMIN.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID checkId) {
        regressionService.delete(SecurityUtils.requireCurrentUserId(), organizationId, projectId, checkId);
        return ResponseEntity.noContent().build();
    }
}
