package com.broksforge.modules.provider.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.security.CredentialEncryptionService;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.domain.AgentCapabilities;
import com.broksforge.modules.agent.repository.AgentRepository;
import com.broksforge.modules.agent.service.CredentialConnectionTester;
import com.broksforge.modules.agent.web.dto.AgentCapabilitiesDto;
import com.broksforge.modules.model.adapter.ProviderAdapter;
import com.broksforge.modules.model.adapter.ProviderAdapterRegistry;
import com.broksforge.modules.model.judge.ChatModelDiscoveryService;
import com.broksforge.modules.model.judge.EmbeddingModelDiscoveryService;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import com.broksforge.modules.provider.web.ProviderMapper;
import com.broksforge.modules.provider.web.dto.ChatModelsResponse;
import com.broksforge.modules.provider.web.dto.CreateProviderRequest;
import com.broksforge.modules.provider.web.dto.EmbeddingModelsResponse;
import com.broksforge.modules.provider.web.dto.ProviderConnectionTestResponse;
import com.broksforge.modules.provider.web.dto.ProviderResponse;
import com.broksforge.modules.provider.web.dto.UpdateProviderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for the Provider aggregate (Provider abstraction milestone): the
 * shared connection/authentication/capability profile agents reference instead of duplicating
 * provider configuration. CRUD only — request construction/response parsing live in
 * {@code com.broksforge.modules.model.adapter}, and actual credential resolution for outbound
 * calls continues to flow through the existing agent-level {@code AgentCredentialService}
 * (see the migration report for why secrets are not moved here automatically).
 */
@Slf4j
@Service
public class ProviderService {

    private final ProviderRepository providerRepository;
    private final AgentRepository agentRepository;
    private final ProviderAccessGuard accessGuard;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final ProviderMapper mapper;
    private final CredentialEncryptionService encryptionService;
    private final EmbeddingModelDiscoveryService embeddingModelDiscoveryService;
    private final ChatModelDiscoveryService chatModelDiscoveryService;
    private final ProviderAdapterRegistry adapterRegistry;
    private final CredentialConnectionTester connectionTester;

    public ProviderService(ProviderRepository providerRepository, AgentRepository agentRepository,
                           ProviderAccessGuard accessGuard, OrganizationAccessService accessService,
                           ProjectService projectService, ProviderMapper mapper,
                           CredentialEncryptionService encryptionService,
                           EmbeddingModelDiscoveryService embeddingModelDiscoveryService,
                           ChatModelDiscoveryService chatModelDiscoveryService,
                           ProviderAdapterRegistry adapterRegistry,
                           CredentialConnectionTester connectionTester) {
        this.providerRepository = providerRepository;
        this.agentRepository = agentRepository;
        this.accessGuard = accessGuard;
        this.accessService = accessService;
        this.projectService = projectService;
        this.mapper = mapper;
        this.encryptionService = encryptionService;
        this.embeddingModelDiscoveryService = embeddingModelDiscoveryService;
        this.chatModelDiscoveryService = chatModelDiscoveryService;
        this.adapterRegistry = adapterRegistry;
        this.connectionTester = connectionTester;
    }

    /**
     * One-shot reachability/credential test against this provider's own endpoint (Providers page
     * "Test connection" action) — reuses the same provider-aware {@link HealthProbePlanner}-driven
     * probe as agent health checks and credential tests, so an Ollama provider is validated via
     * {@code GET /api/tags}, an OpenAI-compatible one via {@code GET /models}, etc. Read-only, same
     * access level as {@link #get}.
     */
    @Transactional(readOnly = true)
    public ProviderConnectionTestResponse testConnection(UUID actorId, UUID organizationId, UUID projectId,
                                                         UUID providerId) {
        Provider provider = accessGuard.requireReadable(organizationId, projectId, providerId, actorId);
        String apiKey = provider.getEncryptedApiKey() != null
                ? encryptionService.decrypt(provider.getEncryptedApiKey()) : null;
        ProviderAdapter adapter = adapterRegistry.resolve(provider.getBaseUrl());
        Map<String, String> authHeaders = adapter != null
                ? adapter.buildAuthHeaders(apiKey)
                : (apiKey == null || apiKey.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + apiKey));
        CredentialConnectionTester.Result result =
                connectionTester.test(provider.getBaseUrl(), null, provider.getType(), authHeaders);
        return new ProviderConnectionTestResponse(result.success(), result.httpStatus(), result.latencyMs(),
                result.message(), result.probeStrategy(), result.probeUrl());
    }

    /**
     * Lists embedding models actually available for this provider (Semantic Similarity metric
     * editor), so users pick a real model id instead of guessing one and hitting a 404 at
     * evaluation time. Read-only, same access level as {@link #get}.
     */
    @Transactional(readOnly = true)
    public EmbeddingModelsResponse listEmbeddingModels(UUID actorId, UUID organizationId, UUID projectId,
                                                       UUID providerId) {
        Provider provider = accessGuard.requireReadable(organizationId, projectId, providerId, actorId);
        EmbeddingModelDiscoveryService.Result result = embeddingModelDiscoveryService.listEmbeddingModels(provider);
        return new EmbeddingModelsResponse(result.supported(), result.models(), result.message());
    }

    /**
     * Lists chat/judge-capable models actually available for this provider (LLM Judge/
     * Hallucination Detection/Citation Verification metric editors). Read-only, same access
     * level as {@link #get}.
     */
    @Transactional(readOnly = true)
    public ChatModelsResponse listChatModels(UUID actorId, UUID organizationId, UUID projectId, UUID providerId) {
        Provider provider = accessGuard.requireReadable(organizationId, projectId, providerId, actorId);
        ChatModelDiscoveryService.Result result = chatModelDiscoveryService.listChatModels(provider);
        return new ChatModelsResponse(result.supported(), result.models(), result.message());
    }

    @Transactional
    public ProviderResponse create(UUID actorId, UUID organizationId, UUID projectId, CreateProviderRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        projectService.assertProjectExists(organizationId, projectId);

        Provider provider = new Provider();
        provider.setOrganizationId(organizationId);
        provider.setProjectId(projectId);
        provider.setName(request.name().trim());
        provider.setType(request.type());
        provider.setBaseUrl(request.baseUrl().trim());
        provider.setAuthType(request.authType() != null ? request.authType() : AgentAuthType.NONE);
        applyApiKey(provider, provider.getAuthType(), request.apiKey());
        if (request.defaultHeaders() != null) {
            provider.setDefaultHeaders(new LinkedHashMap<>(request.defaultHeaders()));
        }
        provider.setDefaultModel(trimToNull(request.defaultModel()));
        if (request.supportedModels() != null) {
            provider.setSupportedModels(List.copyOf(request.supportedModels()));
        }
        applyCapabilities(provider.getCapabilities(), request.capabilities());
        if (request.rateLimits() != null) {
            provider.setRateLimits(new LinkedHashMap<>(request.rateLimits()));
        }
        if (request.pricingMetadata() != null) {
            provider.setPricingMetadata(new LinkedHashMap<>(request.pricingMetadata()));
        }

        Provider saved = providerRepository.save(provider);
        log.info("Provider {} ('{}', type {}) created in project {} by {}", saved.getId(), saved.getName(),
                saved.getType(), projectId, actorId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProviderResponse> list(UUID actorId, UUID organizationId, UUID projectId, Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        return PageResponse.from(providerRepository.findByProjectIdAndDeletedFalse(projectId, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ProviderResponse get(UUID actorId, UUID organizationId, UUID projectId, UUID providerId) {
        Provider provider = accessGuard.requireReadable(organizationId, projectId, providerId, actorId);
        return toResponse(provider);
    }

    @Transactional
    public ProviderResponse update(UUID actorId, UUID organizationId, UUID projectId, UUID providerId,
                                   UpdateProviderRequest request) {
        Provider provider = accessGuard.requireManageable(organizationId, projectId, providerId, actorId,
                OrganizationRole.MEMBER);

        if (StringUtils.hasText(request.name())) {
            provider.setName(request.name().trim());
        }
        if (StringUtils.hasText(request.baseUrl())) {
            provider.setBaseUrl(request.baseUrl().trim());
        }
        if (request.authType() != null) {
            provider.setAuthType(request.authType());
        }
        if (StringUtils.hasText(request.apiKey())) {
            applyApiKey(provider, provider.getAuthType(), request.apiKey());
        } else if (request.authType() == AgentAuthType.NONE) {
            provider.setEncryptedApiKey(null);
            provider.setApiKeyHint(null);
        }
        if (request.defaultHeaders() != null) {
            provider.setDefaultHeaders(new LinkedHashMap<>(request.defaultHeaders()));
        }
        if (request.defaultModel() != null) {
            provider.setDefaultModel(trimToNull(request.defaultModel()));
        }
        if (request.supportedModels() != null) {
            provider.setSupportedModels(List.copyOf(request.supportedModels()));
        }
        if (request.capabilities() != null) {
            applyCapabilities(provider.getCapabilities(), request.capabilities());
        }
        if (request.rateLimits() != null) {
            provider.setRateLimits(new LinkedHashMap<>(request.rateLimits()));
        }
        if (request.pricingMetadata() != null) {
            provider.setPricingMetadata(new LinkedHashMap<>(request.pricingMetadata()));
        }

        log.info("Provider {} updated in project {} by {}", providerId, projectId, actorId);
        return toResponse(provider);
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId, UUID providerId) {
        Provider provider = accessGuard.requireManageable(organizationId, projectId, providerId, actorId,
                OrganizationRole.ADMIN);
        int linked = agentRepository.countByProviderIdAndDeletedFalse(providerId);
        if (linked > 0) {
            throw new ResourceConflictException(ErrorCode.PROVIDER_IN_USE,
                    "This provider is still linked to %d agent(s); unlink them before deleting it".formatted(linked));
        }
        provider.softDelete(actorId);
        log.info("Provider {} soft-deleted in project {} by {}", providerId, projectId, actorId);
    }

    /** Creates an independent copy of {@code providerId} — same config (including the API key), new id. */
    @Transactional
    public ProviderResponse duplicate(UUID actorId, UUID organizationId, UUID projectId, UUID providerId) {
        Provider source = accessGuard.requireManageable(organizationId, projectId, providerId, actorId,
                OrganizationRole.MEMBER);

        Provider copy = new Provider();
        copy.setOrganizationId(organizationId);
        copy.setProjectId(projectId);
        copy.setName(source.getName() + " (copy)");
        copy.setType(source.getType());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setAuthType(source.getAuthType());
        // The ciphertext is copied verbatim (not decrypted/re-encrypted) — it's already valid,
        // tenant-scoped AES-256-GCM ciphertext under the same active key, so this never touches
        // the plaintext secret.
        copy.setEncryptedApiKey(source.getEncryptedApiKey());
        copy.setApiKeyHint(source.getApiKeyHint());
        copy.setKeyVersion(source.getKeyVersion());
        copy.setDefaultHeaders(new LinkedHashMap<>(source.getDefaultHeaders()));
        copy.setDefaultModel(source.getDefaultModel());
        copy.setSupportedModels(List.copyOf(source.getSupportedModels()));
        copy.getCapabilities().setStreaming(source.getCapabilities().isStreaming());
        copy.getCapabilities().setMemory(source.getCapabilities().isMemory());
        copy.getCapabilities().setRag(source.getCapabilities().isRag());
        copy.getCapabilities().setToolCalling(source.getCapabilities().isToolCalling());
        copy.getCapabilities().setStructuredOutput(source.getCapabilities().isStructuredOutput());
        copy.getCapabilities().setReasoning(source.getCapabilities().isReasoning());
        copy.getCapabilities().setMultiAgent(source.getCapabilities().isMultiAgent());
        copy.getCapabilities().setCustomMetadata(new LinkedHashMap<>(source.getCapabilities().getCustomMetadata()));
        copy.setRateLimits(new LinkedHashMap<>(source.getRateLimits()));
        copy.setPricingMetadata(new LinkedHashMap<>(source.getPricingMetadata()));
        // A duplicate starts enabled with a fresh (unknown) health status — it hasn't been
        // probed yet, and copying "healthy" from the source would be an unverified claim about
        // a distinct configuration the platform has never actually called.

        Provider saved = providerRepository.save(copy);
        log.info("Provider {} duplicated to {} ('{}') in project {} by {}", providerId, saved.getId(),
                saved.getName(), projectId, actorId);
        return toResponse(saved);
    }

    @Transactional
    public ProviderResponse setEnabled(UUID actorId, UUID organizationId, UUID projectId, UUID providerId,
                                       boolean enabled) {
        Provider provider = accessGuard.requireManageable(organizationId, projectId, providerId, actorId,
                OrganizationRole.MEMBER);
        provider.setEnabled(enabled);
        log.info("Provider {} {} in project {} by {}", providerId, enabled ? "enabled" : "disabled", projectId,
                actorId);
        return toResponse(provider);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private ProviderResponse toResponse(Provider provider) {
        int linkedAgentCount = agentRepository.countByProviderIdAndDeletedFalse(provider.getId());
        return mapper.toResponse(provider, provider.getEncryptedApiKey() != null, linkedAgentCount,
                provider.getSupportedModels().size());
    }

    private void applyApiKey(Provider provider, AgentAuthType authType, String apiKey) {
        if (authType == null || authType == AgentAuthType.NONE || !StringUtils.hasText(apiKey)) {
            return;
        }
        provider.setEncryptedApiKey(encryptionService.encrypt(apiKey));
        provider.setApiKeyHint(mask(apiKey));
        provider.setKeyVersion(encryptionService.currentKeyVersion());
    }

    private void applyCapabilities(AgentCapabilities target, AgentCapabilitiesDto dto) {
        if (dto == null) {
            return;
        }
        target.setStreaming(dto.streaming());
        target.setMemory(dto.memory());
        target.setRag(dto.rag());
        target.setToolCalling(dto.toolCalling());
        target.setStructuredOutput(dto.structuredOutput());
        target.setReasoning(dto.reasoning());
        target.setMultiAgent(dto.multiAgent());
        target.setCustomMetadata(dto.customMetadata() != null
                ? new LinkedHashMap<>(dto.customMetadata()) : new LinkedHashMap<>());
    }

    private String mask(String secret) {
        if (secret == null || secret.isEmpty()) {
            return null;
        }
        return secret.length() <= 4 ? "••••" : "••••" + secret.substring(secret.length() - 4);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
