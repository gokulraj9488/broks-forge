package com.broksforge.modules.prompt.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.prompt.service.PromptService;
import com.broksforge.modules.prompt.web.dto.CreatePromptRequest;
import com.broksforge.modules.prompt.web.dto.PromptFilter;
import com.broksforge.modules.prompt.web.dto.PromptResponse;
import com.broksforge.modules.prompt.web.dto.PromptSummaryResponse;
import com.broksforge.modules.prompt.web.dto.UpdatePromptRequest;
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
 * Prompt-library CRUD, search and lifecycle. Versioning, activation/rollback and
 * comparison live in {@link PromptVersionController}.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/prompts")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Prompts", description = "Manage reusable, versioned prompt templates")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @PostMapping
    @Operation(summary = "Create a prompt")
    public ResponseEntity<PromptResponse> create(@PathVariable UUID organizationId,
                                                 @PathVariable UUID projectId,
                                                 @Valid @RequestBody CreatePromptRequest request) {
        PromptResponse response =
                promptService.create(SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List & search prompts")
    public ResponseEntity<PageResponse<PromptSummaryResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @ParameterObject PromptFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(promptService.search(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, filter, pageable));
    }

    @GetMapping("/{promptId}")
    @Operation(summary = "Get a prompt")
    public ResponseEntity<PromptResponse> get(@PathVariable UUID organizationId,
                                              @PathVariable UUID projectId,
                                              @PathVariable UUID promptId) {
        return ResponseEntity.ok(promptService.get(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId));
    }

    @PatchMapping("/{promptId}")
    @Operation(summary = "Update a prompt")
    public ResponseEntity<PromptResponse> update(@PathVariable UUID organizationId,
                                                 @PathVariable UUID projectId,
                                                 @PathVariable UUID promptId,
                                                 @Valid @RequestBody UpdatePromptRequest request) {
        return ResponseEntity.ok(promptService.update(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId, request));
    }

    @DeleteMapping("/{promptId}")
    @Operation(summary = "Delete a prompt", description = "Soft-deletes the prompt. Requires ADMIN or higher.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID promptId) {
        promptService.delete(SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{promptId}/archive")
    @Operation(summary = "Archive a prompt")
    public ResponseEntity<PromptResponse> archive(@PathVariable UUID organizationId,
                                                  @PathVariable UUID projectId,
                                                  @PathVariable UUID promptId) {
        return ResponseEntity.ok(promptService.archive(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId));
    }

    @PostMapping("/{promptId}/unarchive")
    @Operation(summary = "Unarchive a prompt")
    public ResponseEntity<PromptResponse> unarchive(@PathVariable UUID organizationId,
                                                    @PathVariable UUID projectId,
                                                    @PathVariable UUID promptId) {
        return ResponseEntity.ok(promptService.unarchive(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, promptId));
    }
}
