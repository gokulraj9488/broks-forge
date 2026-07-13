package com.broksforge.modules.evaluation.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.evaluation.service.EvaluationProfileService;
import com.broksforge.modules.evaluation.web.dto.CreateEvaluationProfileRequest;
import com.broksforge.modules.evaluation.web.dto.EvaluationProfileResponse;
import com.broksforge.modules.evaluation.web.dto.UpdateEvaluationProfileRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * CRUD for reusable evaluation profiles (metric rubrics applied by jobs).
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/evaluation-profiles")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Evaluation profiles", description = "Reusable metric + threshold rubrics")
public class EvaluationProfileController {

    private final EvaluationProfileService profileService;

    public EvaluationProfileController(EvaluationProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping
    @Operation(summary = "Create an evaluation profile")
    public ResponseEntity<EvaluationProfileResponse> create(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateEvaluationProfileRequest request) {
        EvaluationProfileResponse response =
                profileService.create(SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List evaluation profiles")
    public ResponseEntity<PageResponse<EvaluationProfileResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(profileService.list(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, search, pageable));
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "Get an evaluation profile")
    public ResponseEntity<EvaluationProfileResponse> get(@PathVariable UUID organizationId,
                                                         @PathVariable UUID projectId,
                                                         @PathVariable UUID profileId) {
        return ResponseEntity.ok(profileService.get(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, profileId));
    }

    @PatchMapping("/{profileId}")
    @Operation(summary = "Update an evaluation profile")
    public ResponseEntity<EvaluationProfileResponse> update(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @PathVariable UUID profileId,
            @Valid @RequestBody UpdateEvaluationProfileRequest request) {
        return ResponseEntity.ok(profileService.update(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, profileId, request));
    }

    @DeleteMapping("/{profileId}")
    @Operation(summary = "Delete an evaluation profile", description = "Soft-deletes the profile. Requires ADMIN.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID profileId) {
        profileService.delete(SecurityUtils.requireCurrentUserId(), organizationId, projectId, profileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{profileId}/duplicate")
    @Operation(summary = "Duplicate an evaluation profile",
            description = "Creates an independent copy (new id, ' (copy)' name suffix, version 1 seeded from the source's current version).")
    public ResponseEntity<EvaluationProfileResponse> duplicate(@PathVariable UUID organizationId,
                                                                @PathVariable UUID projectId,
                                                                @PathVariable UUID profileId) {
        EvaluationProfileResponse response = profileService.duplicate(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, profileId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{profileId}/enable")
    @Operation(summary = "Enable an evaluation profile for selection in new jobs")
    public ResponseEntity<EvaluationProfileResponse> enable(@PathVariable UUID organizationId,
                                                             @PathVariable UUID projectId,
                                                             @PathVariable UUID profileId) {
        return ResponseEntity.ok(profileService.setEnabled(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, profileId, true));
    }

    @PostMapping("/{profileId}/disable")
    @Operation(summary = "Disable an evaluation profile from selection in new jobs")
    public ResponseEntity<EvaluationProfileResponse> disable(@PathVariable UUID organizationId,
                                                              @PathVariable UUID projectId,
                                                              @PathVariable UUID profileId) {
        return ResponseEntity.ok(profileService.setEnabled(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, profileId, false));
    }
}
