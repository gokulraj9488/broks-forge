package com.broksforge.modules.agent.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.security.CredentialEncryptionService;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.domain.AgentCredential;
import com.broksforge.modules.agent.repository.AgentCredentialRepository;
import com.broksforge.modules.agent.web.AgentCredentialMapper;
import com.broksforge.modules.agent.web.dto.AgentCredentialResponse;
import com.broksforge.modules.agent.web.dto.SetAgentCredentialRequest;
import com.broksforge.modules.organization.domain.OrganizationRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages an agent's authentication credentials.
 *
 * <p>Secrets are encrypted with AES-256-GCM and stored as ciphertext (never
 * plaintext, never hashed — see ADR 0003). Only non-sensitive metadata is ever
 * returned by the API. Decrypted material is produced solely for internal
 * outbound calls via {@link #resolveAuthHeaders(Agent)}.</p>
 */
@Slf4j
@Service
public class AgentCredentialService {

    private static final String DEFAULT_API_KEY_HEADER = "X-API-Key";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final AgentCredentialRepository credentialRepository;
    private final AgentAccessGuard accessGuard;
    private final AgentCredentialMapper mapper;
    private final CredentialEncryptionService encryptionService;

    public AgentCredentialService(AgentCredentialRepository credentialRepository,
                                  AgentAccessGuard accessGuard,
                                  AgentCredentialMapper mapper,
                                  CredentialEncryptionService encryptionService) {
        this.credentialRepository = credentialRepository;
        this.accessGuard = accessGuard;
        this.mapper = mapper;
        this.encryptionService = encryptionService;
    }

    @Transactional
    public AgentCredentialResponse set(UUID actorId, UUID organizationId, UUID projectId, UUID agentId,
                                       SetAgentCredentialRequest request) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.ADMIN);
        accessGuard.ensureNotArchived(agent);
        validate(request);

        // Only one active credential at a time; previous ones are retained inactive for audit.
        credentialRepository.findByAgentIdOrderByCreatedAtDesc(agentId)
                .forEach(existing -> existing.setActive(false));

        AgentCredential credential = new AgentCredential();
        credential.setAgentId(agentId);
        credential.setOrganizationId(organizationId);
        credential.setProjectId(projectId);
        credential.setAuthType(request.authType());
        credential.setUsername(trimToNull(request.username()));
        credential.setHeaderName(resolveHeaderName(request));
        credential.setActive(true);

        if (request.authType().requiresSecret()) {
            credential.setEncryptedSecret(encryptionService.encrypt(request.secret()));
            credential.setSecretHint(mask(request.secret()));
            credential.setKeyVersion(encryptionService.currentKeyVersion());
        }

        AgentCredential saved = credentialRepository.save(credential);
        // Keep the agent's declared auth type aligned with its active credential.
        agent.setAuthType(request.authType());

        log.info("Credential set for agent {} (type {}) by {}", agentId, request.authType(), actorId);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AgentCredentialResponse> list(UUID actorId, UUID organizationId, UUID projectId, UUID agentId) {
        accessGuard.requireManageable(organizationId, projectId, agentId, actorId, OrganizationRole.ADMIN);
        return credentialRepository.findByAgentIdOrderByCreatedAtDesc(agentId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId, UUID agentId, UUID credentialId) {
        accessGuard.requireManageable(organizationId, projectId, agentId, actorId, OrganizationRole.ADMIN);
        AgentCredential credential = credentialRepository.findByIdAndAgentId(credentialId, agentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Agent credential", credentialId));
        credentialRepository.delete(credential);
        log.info("Credential {} deleted for agent {} by {}", credentialId, agentId, actorId);
    }

    /**
     * Produces the HTTP headers required to authenticate against the agent's
     * endpoint, decrypting the active credential. Internal use only (health
     * probes and future invocation); never exposed via the API.
     */
    @Transactional(readOnly = true)
    public Map<String, String> resolveAuthHeaders(Agent agent) {
        AgentCredential credential = credentialRepository
                .findFirstByAgentIdAndActiveTrueOrderByCreatedAtDesc(agent.getId())
                .orElse(null);
        if (credential == null || credential.getAuthType() == AgentAuthType.NONE) {
            return Map.of();
        }
        String secret = encryptionService.decrypt(credential.getEncryptedSecret());
        Map<String, String> headers = new LinkedHashMap<>();
        switch (credential.getAuthType()) {
            case API_KEY -> headers.put(
                    credential.getHeaderName() != null ? credential.getHeaderName() : DEFAULT_API_KEY_HEADER,
                    secret);
            case BEARER_TOKEN -> headers.put(AUTHORIZATION_HEADER, "Bearer " + secret);
            case BASIC_AUTH -> {
                String raw = (credential.getUsername() == null ? "" : credential.getUsername()) + ":" + secret;
                headers.put(AUTHORIZATION_HEADER,
                        "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
            }
            case CUSTOM_HEADER -> {
                if (credential.getHeaderName() != null) {
                    headers.put(credential.getHeaderName(), secret);
                }
            }
            default -> { /* NONE handled above */ }
        }
        return headers;
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private void validate(SetAgentCredentialRequest request) {
        AgentAuthType type = request.authType();
        boolean hasSecret = StringUtils.hasText(request.secret());
        switch (type) {
            case NONE -> {
                if (hasSecret) {
                    throw typeMismatch("Authentication type NONE must not include a secret");
                }
            }
            case API_KEY, BEARER_TOKEN -> {
                if (!hasSecret) {
                    throw typeMismatch("A secret is required for this authentication type");
                }
            }
            case BASIC_AUTH -> {
                if (!hasSecret || !StringUtils.hasText(request.username())) {
                    throw typeMismatch("BASIC_AUTH requires both a username and a secret");
                }
            }
            case CUSTOM_HEADER -> {
                if (!hasSecret || !StringUtils.hasText(request.headerName())) {
                    throw typeMismatch("CUSTOM_HEADER requires both a header name and a secret");
                }
            }
            default -> throw typeMismatch("Unsupported authentication type");
        }
    }

    private String resolveHeaderName(SetAgentCredentialRequest request) {
        return switch (request.authType()) {
            case API_KEY -> StringUtils.hasText(request.headerName())
                    ? request.headerName().trim() : DEFAULT_API_KEY_HEADER;
            case CUSTOM_HEADER -> trimToNull(request.headerName());
            default -> null;
        };
    }

    private BadRequestException typeMismatch(String message) {
        return new BadRequestException(ErrorCode.CREDENTIAL_TYPE_MISMATCH, message);
    }

    private String mask(String secret) {
        if (secret == null || secret.isEmpty()) {
            return null;
        }
        if (secret.length() <= 4) {
            return "••••";
        }
        return "••••" + secret.substring(secret.length() - 4);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
