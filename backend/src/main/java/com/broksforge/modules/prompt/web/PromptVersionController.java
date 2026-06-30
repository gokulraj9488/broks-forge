package com.broksforge.modules.prompt.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.prompt.service.PromptService;
import com.broksforge.modules.prompt.web.dto.CreatePromptVersionRequest;
import com.broksforge.modules.prompt.web.dto.PromptComparisonResponse;
import com.broksforge.modules.prompt.web.dto.PromptVersionResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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
 * Prompt versioning: create, list, inspect, activate, roll back and compare
 * immutable prompt versions.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/prompts/{promptId}")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Prompt versions", description = "Version, activate, roll back and compare prompts")
public class PromptVersionController {

    private final PromptService promptService;

    public PromptVersionController(PromptService promptService) {
        this.promptService = promptService;
    }

    @PostMapping("/versions")
    @Operation(summary = "Create a prompt version",
            description = "Stores a new immutable template version; variables are derived from {{placeholders}}.")
    public ResponseEntity<PromptVersionResponse> createVersion(@PathVariable UUID organizationId,
                                                               @PathVariable UUID projectId,
                                                               @PathVariable UUID promptId,
                                                               @Valid @RequestBody CreatePromptVersionRequest request) {
        PromptVersionResponse response = promptService.createVersion(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/versions")
    @Operation(summary = "List prompt versions")
    public ResponseEntity<PageResponse<PromptVersionResponse>> listVersions(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @PathVariable UUID promptId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(promptService.listVersions(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId, pageable));
    }

    @GetMapping("/versions/{versionId}")
    @Operation(summary = "Get a prompt version")
    public ResponseEntity<PromptVersionResponse> getVersion(@PathVariable UUID organizationId,
                                                            @PathVariable UUID projectId,
                                                            @PathVariable UUID promptId,
                                                            @PathVariable UUID versionId) {
        return ResponseEntity.ok(promptService.getVersion(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId, versionId));
    }

    @PostMapping("/versions/{versionId}/activate")
    @Operation(summary = "Activate a prompt version")
    public ResponseEntity<PromptVersionResponse> activate(@PathVariable UUID organizationId,
                                                          @PathVariable UUID projectId,
                                                          @PathVariable UUID promptId,
                                                          @PathVariable UUID versionId) {
        return ResponseEntity.ok(promptService.activate(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId, versionId));
    }

    @PostMapping("/versions/{versionId}/rollback")
    @Operation(summary = "Roll back to a prompt version")
    public ResponseEntity<PromptVersionResponse> rollback(@PathVariable UUID organizationId,
                                                          @PathVariable UUID projectId,
                                                          @PathVariable UUID promptId,
                                                          @PathVariable UUID versionId) {
        return ResponseEntity.ok(promptService.rollback(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId, versionId));
    }

    @GetMapping("/compare")
    @Operation(summary = "Compare two prompt versions")
    public ResponseEntity<PromptComparisonResponse> compare(@PathVariable UUID organizationId,
                                                            @PathVariable UUID projectId,
                                                            @PathVariable UUID promptId,
                                                            @RequestParam UUID from,
                                                            @RequestParam UUID to) {
        return ResponseEntity.ok(promptService.compare(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId, from, to));
    }
}
