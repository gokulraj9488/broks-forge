package com.broksforge.modules.provider.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.provider.service.ProviderService;
import com.broksforge.modules.provider.web.dto.ChatModelsResponse;
import com.broksforge.modules.provider.web.dto.CreateProviderRequest;
import com.broksforge.modules.provider.web.dto.EmbeddingModelsResponse;
import com.broksforge.modules.provider.web.dto.ProviderConnectionTestResponse;
import com.broksforge.modules.provider.web.dto.ProviderResponse;
import com.broksforge.modules.provider.web.dto.UpdateProviderRequest;
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
 * Provider registry CRUD (Provider abstraction milestone): the shared connection,
 * authentication and capability profile agents reference instead of duplicating provider
 * configuration.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/providers")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Providers", description = "Register and manage LLM provider configurations")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @PostMapping
    @Operation(summary = "Register a provider", description = "Creates a new provider configuration. Requires "
            + "organization membership.")
    public ResponseEntity<ProviderResponse> create(@PathVariable UUID organizationId,
                                                   @PathVariable UUID projectId,
                                                   @Valid @RequestBody CreateProviderRequest request) {
        ProviderResponse response =
                providerService.create(SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List providers")
    public ResponseEntity<PageResponse<ProviderResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(
                providerService.list(SecurityUtils.requireCurrentUserId(), organizationId, projectId, pageable));
    }

    @GetMapping("/{providerId}")
    @Operation(summary = "Get a provider")
    public ResponseEntity<ProviderResponse> get(@PathVariable UUID organizationId,
                                                @PathVariable UUID projectId,
                                                @PathVariable UUID providerId) {
        return ResponseEntity.ok(
                providerService.get(SecurityUtils.requireCurrentUserId(), organizationId, projectId, providerId));
    }

    @PatchMapping("/{providerId}")
    @Operation(summary = "Update a provider")
    public ResponseEntity<ProviderResponse> update(@PathVariable UUID organizationId,
                                                   @PathVariable UUID projectId,
                                                   @PathVariable UUID providerId,
                                                   @Valid @RequestBody UpdateProviderRequest request) {
        return ResponseEntity.ok(providerService.update(SecurityUtils.requireCurrentUserId(), organizationId,
                projectId, providerId, request));
    }

    @DeleteMapping("/{providerId}")
    @Operation(summary = "Delete a provider", description = "Fails if agents are still linked to it.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID providerId) {
        providerService.delete(SecurityUtils.requireCurrentUserId(), organizationId, projectId, providerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{providerId}/duplicate")
    @Operation(summary = "Duplicate a provider", description = "Creates an independent copy with the same "
            + "configuration (including the API key), a new id, and its own health/usage history.")
    public ResponseEntity<ProviderResponse> duplicate(@PathVariable UUID organizationId,
                                                      @PathVariable UUID projectId,
                                                      @PathVariable UUID providerId) {
        ProviderResponse response =
                providerService.duplicate(SecurityUtils.requireCurrentUserId(), organizationId, projectId, providerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{providerId}/enable")
    @Operation(summary = "Enable a provider", description = "Agents linked to it may be invoked again.")
    public ResponseEntity<ProviderResponse> enable(@PathVariable UUID organizationId,
                                                   @PathVariable UUID projectId,
                                                   @PathVariable UUID providerId) {
        return ResponseEntity.ok(providerService.setEnabled(SecurityUtils.requireCurrentUserId(), organizationId,
                projectId, providerId, true));
    }

    @PostMapping("/{providerId}/disable")
    @Operation(summary = "Disable a provider", description = "New evaluations against agents linked to it are "
            + "rejected until it's re-enabled; already-running jobs are not interrupted.")
    public ResponseEntity<ProviderResponse> disable(@PathVariable UUID organizationId,
                                                    @PathVariable UUID projectId,
                                                    @PathVariable UUID providerId) {
        return ResponseEntity.ok(providerService.setEnabled(SecurityUtils.requireCurrentUserId(), organizationId,
                projectId, providerId, false));
    }

    @GetMapping("/{providerId}/embedding-models")
    @Operation(summary = "List embedding models available for this provider",
            description = "Live-queries the provider's own models endpoint (OpenAI, Google AI Studio, Ollama). "
                    + "Providers with no embeddings API (Anthropic, Groq, OpenRouter) report supported=false.")
    public ResponseEntity<EmbeddingModelsResponse> embeddingModels(@PathVariable UUID organizationId,
                                                                   @PathVariable UUID projectId,
                                                                   @PathVariable UUID providerId) {
        return ResponseEntity.ok(providerService.listEmbeddingModels(SecurityUtils.requireCurrentUserId(),
                organizationId, projectId, providerId));
    }

    @GetMapping("/{providerId}/chat-models")
    @Operation(summary = "List chat/judge-capable models available for this provider",
            description = "Live-queries the provider's own models endpoint — supported for all six integrated "
                    + "provider types (OpenAI, Groq, OpenRouter, Google AI Studio, Ollama, Anthropic).")
    public ResponseEntity<ChatModelsResponse> chatModels(@PathVariable UUID organizationId,
                                                         @PathVariable UUID projectId,
                                                         @PathVariable UUID providerId) {
        return ResponseEntity.ok(providerService.listChatModels(SecurityUtils.requireCurrentUserId(),
                organizationId, projectId, providerId));
    }

    @PostMapping("/{providerId}/test-connection")
    @Operation(summary = "Test the connection to this provider", description = "Performs a one-shot, "
            + "provider-aware reachability/credential probe (the same probe used for agent health checks) "
            + "against this provider's own endpoint and reports the exact method/URL/status observed.")
    public ResponseEntity<ProviderConnectionTestResponse> testConnection(@PathVariable UUID organizationId,
                                                                         @PathVariable UUID projectId,
                                                                         @PathVariable UUID providerId) {
        return ResponseEntity.ok(providerService.testConnection(SecurityUtils.requireCurrentUserId(),
                organizationId, projectId, providerId));
    }
}
