package com.broksforge.modules.apikey.service;

import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.util.SecureTokens;
import com.broksforge.common.web.PageResponse;
import com.broksforge.config.properties.ApiKeyProperties;
import com.broksforge.modules.apikey.domain.ApiKey;
import com.broksforge.modules.apikey.repository.ApiKeyRepository;
import com.broksforge.modules.apikey.web.ApiKeyMapper;
import com.broksforge.modules.apikey.web.dto.ApiKeyResponse;
import com.broksforge.modules.apikey.web.dto.CreateApiKeyRequest;
import com.broksforge.modules.apikey.web.dto.CreatedApiKeyResponse;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import com.broksforge.security.apikey.ApiKeyAuthenticator;
import com.broksforge.security.apikey.ApiKeyPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages project API keys and authenticates inbound keys (implementing the
 * {@link ApiKeyAuthenticator} port consumed by the security layer).
 *
 * <p>Keys are formatted as {@code <prefix>_<publicId>.<secret>}. Only the public
 * portion and the SHA-256 hash of the secret are persisted; the raw key is shown
 * to the user exactly once.</p>
 */
@Slf4j
@Service
public class ApiKeyService implements ApiKeyAuthenticator {

    private static final char SECRET_SEPARATOR = '.';
    private static final int PUBLIC_ID_BYTES = 6;   // -> 12 hex chars

    private final ApiKeyRepository apiKeyRepository;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final ApiKeyMapper mapper;
    private final ApiKeyProperties apiKeyProperties;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         OrganizationAccessService accessService,
                         ProjectService projectService,
                         ApiKeyMapper mapper,
                         ApiKeyProperties apiKeyProperties) {
        this.apiKeyRepository = apiKeyRepository;
        this.accessService = accessService;
        this.projectService = projectService;
        this.mapper = mapper;
        this.apiKeyProperties = apiKeyProperties;
    }

    // ----------------------------------------------------------------------
    // Management (JWT-authenticated)
    // ----------------------------------------------------------------------

    @Transactional
    public CreatedApiKeyResponse create(UUID actorId, UUID organizationId, UUID projectId,
                                        CreateApiKeyRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.ADMIN);
        projectService.assertProjectExists(organizationId, projectId);

        String publicId = SecureTokens.randomHex(PUBLIC_ID_BYTES);
        String keyPrefix = apiKeyProperties.prefix() + "_" + publicId;
        String secret = SecureTokens.generateToken();
        String plaintextKey = keyPrefix + SECRET_SEPARATOR + secret;

        ApiKey apiKey = new ApiKey();
        apiKey.setOrganizationId(organizationId);
        apiKey.setProjectId(projectId);
        apiKey.setName(request.name().trim());
        apiKey.setKeyPrefix(keyPrefix);
        apiKey.setSecretHash(SecureTokens.sha256Hex(secret));
        if (request.expiresInDays() != null) {
            apiKey.setExpiresAt(Instant.now().plus(request.expiresInDays(), ChronoUnit.DAYS));
        }
        ApiKey saved = apiKeyRepository.save(apiKey);

        log.info("API key {} ('{}') created for project {} by {}",
                saved.getId(), keyPrefix, projectId, actorId);
        return new CreatedApiKeyResponse(plaintextKey, mapper.toResponse(saved));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApiKeyResponse> list(UUID actorId, UUID organizationId, UUID projectId, Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        return PageResponse.from(apiKeyRepository.findByProjectId(projectId, pageable), mapper::toResponse);
    }

    @Transactional
    public void revoke(UUID actorId, UUID organizationId, UUID projectId, UUID apiKeyId) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.ADMIN);
        ApiKey apiKey = apiKeyRepository.findByIdAndProjectId(apiKeyId, projectId)
                .filter(key -> key.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> ResourceNotFoundException.of("API key", apiKeyId));
        apiKey.revoke();
        log.info("API key {} revoked for project {} by {}", apiKeyId, projectId, actorId);
    }

    // ----------------------------------------------------------------------
    // Authentication (ApiKeyAuthenticator port)
    // ----------------------------------------------------------------------

    @Override
    @Transactional
    public Optional<ApiKeyPrincipal> authenticate(String rawKey) {
        if (rawKey == null) {
            return Optional.empty();
        }
        int separator = rawKey.indexOf(SECRET_SEPARATOR);
        if (separator <= 0 || separator == rawKey.length() - 1) {
            return Optional.empty();
        }
        String keyPrefix = rawKey.substring(0, separator);
        String secret = rawKey.substring(separator + 1);

        ApiKey apiKey = apiKeyRepository.findByKeyPrefix(keyPrefix).orElse(null);
        if (apiKey == null || !apiKey.isActive()) {
            return Optional.empty();
        }
        String presentedHash = SecureTokens.sha256Hex(secret);
        if (!SecureTokens.constantTimeEquals(presentedHash, apiKey.getSecretHash())) {
            return Optional.empty();
        }

        apiKey.setLastUsedAt(Instant.now());
        return Optional.of(new ApiKeyPrincipal(
                apiKey.getId(), apiKey.getProjectId(), apiKey.getOrganizationId(), apiKey.getName()));
    }
}
