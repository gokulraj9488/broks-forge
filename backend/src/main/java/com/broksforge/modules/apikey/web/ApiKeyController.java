package com.broksforge.modules.apikey.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.apikey.service.ApiKeyService;
import com.broksforge.modules.apikey.web.dto.ApiKeyResponse;
import com.broksforge.modules.apikey.web.dto.CreateApiKeyRequest;
import com.broksforge.modules.apikey.web.dto.CreatedApiKeyResponse;
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

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/api-keys")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "API Keys", description = "Issue and revoke project API keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @Operation(summary = "Create an API key",
            description = "Returns the full key once. Store it securely; it cannot be retrieved again. "
                    + "Requires ADMIN or higher.")
    public ResponseEntity<CreatedApiKeyResponse> create(@PathVariable UUID organizationId,
                                                        @PathVariable UUID projectId,
                                                        @Valid @RequestBody CreateApiKeyRequest request) {
        CreatedApiKeyResponse response =
                apiKeyService.create(SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List API keys", description = "Lists key metadata (never secrets).")
    public ResponseEntity<PageResponse<ApiKeyResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(
                apiKeyService.list(SecurityUtils.requireCurrentUserId(), organizationId, projectId, pageable));
    }

    @DeleteMapping("/{apiKeyId}")
    @Operation(summary = "Revoke an API key", description = "Permanently revokes the key. Requires ADMIN or higher.")
    public ResponseEntity<Void> revoke(@PathVariable UUID organizationId,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID apiKeyId) {
        apiKeyService.revoke(SecurityUtils.requireCurrentUserId(), organizationId, projectId, apiKeyId);
        return ResponseEntity.noContent().build();
    }
}
