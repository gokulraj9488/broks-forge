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
import com.broksforge.modules.agent.web.dto.CredentialTestResponse;
import com.broksforge.modules.agent.web.dto.SetAgentCredentialRequest;
import com.broksforge.modules.agent.web.dto.TestAgentCredentialRequest;
import com.broksforge.modules.agent.web.dto.UpdateAgentCredentialRequest;
import com.broksforge.modules.organization.domain.OrganizationRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
 * outbound calls via {@link #resolveAuthHeaders(Agent)} and for connection
 * tests (which never return the secret, only a verdict).</p>
 */
@Slf4j
@Service
public class AgentCredentialService {

    private static final String DEFAULT_API_KEY_HEADER = "X-API-Key";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String DEFAULT_BEARER_PREFIX = "Bearer";

    private final AgentCredentialRepository credentialRepository;
    private final AgentAccessGuard accessGuard;
    private final AgentCredentialMapper mapper;
    private final CredentialEncryptionService encryptionService;
    private final CredentialConnectionTester connectionTester;

    public AgentCredentialService(AgentCredentialRepository credentialRepository,
                                  AgentAccessGuard accessGuard,
                                  AgentCredentialMapper mapper,
                                  CredentialEncryptionService encryptionService,
                                  CredentialConnectionTester connectionTester) {
        this.credentialRepository = credentialRepository;
        this.accessGuard = accessGuard;
        this.mapper = mapper;
        this.encryptionService = encryptionService;
        this.connectionTester = connectionTester;
    }

    /** Creates a new active credential, deactivating (but retaining) any previous ones — i.e. "replace". */
    @Transactional
    public AgentCredentialResponse set(UUID actorId, UUID organizationId, UUID projectId, UUID agentId,
                                       SetAgentCredentialRequest request) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.ADMIN);
        accessGuard.ensureNotArchived(agent);
        requireFields(request.authType(), StringUtils.hasText(request.secret()),
                StringUtils.hasText(request.username()), StringUtils.hasText(request.headerName()));

        // Only one active credential at a time; previous ones are retained inactive for audit.
        credentialRepository.findByAgentIdOrderByCreatedAtDesc(agentId)
                .forEach(existing -> existing.setActive(false));

        AgentCredential credential = new AgentCredential();
        credential.setAgentId(agentId);
        credential.setOrganizationId(organizationId);
        credential.setProjectId(projectId);
        credential.setLabel(trimToNull(request.label()));
        credential.setAuthType(request.authType());
        credential.setUsername(trimToNull(request.username()));
        credential.setHeaderName(resolveHeaderName(request.authType(), request.headerName()));
        credential.setHeaderPrefix(resolveHeaderPrefix(request.authType(), request.headerPrefix()));
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

    /** Edits an existing credential in place. A blank secret keeps the stored one; a non-blank secret rotates it. */
    @Transactional
    public AgentCredentialResponse update(UUID actorId, UUID organizationId, UUID projectId, UUID agentId,
                                          UUID credentialId, UpdateAgentCredentialRequest request) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.ADMIN);
        accessGuard.ensureNotArchived(agent);
        AgentCredential credential = credentialRepository.findByIdAndAgentId(credentialId, agentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Agent credential", credentialId));

        boolean rotatingSecret = StringUtils.hasText(request.secret());
        boolean effectiveHasSecret = rotatingSecret || credential.getEncryptedSecret() != null;
        requireFields(request.authType(), effectiveHasSecret,
                StringUtils.hasText(request.username()), StringUtils.hasText(request.headerName()));

        credential.setLabel(trimToNull(request.label()));
        credential.setAuthType(request.authType());
        credential.setUsername(trimToNull(request.username()));
        credential.setHeaderName(resolveHeaderName(request.authType(), request.headerName()));
        credential.setHeaderPrefix(resolveHeaderPrefix(request.authType(), request.headerPrefix()));

        if (request.authType() == AgentAuthType.NONE) {
            credential.setEncryptedSecret(null);
            credential.setSecretHint(null);
        } else if (rotatingSecret) {
            credential.setEncryptedSecret(encryptionService.encrypt(request.secret()));
            credential.setSecretHint(mask(request.secret()));
            credential.setKeyVersion(encryptionService.currentKeyVersion());
        }
        // Any change to the auth material makes a prior connection-test result meaningless.
        credential.clearTestResult();

        if (credential.isActive()) {
            agent.setAuthType(request.authType());
        }
        log.info("Credential {} updated for agent {} by {}", credentialId, agentId, actorId);
        return mapper.toResponse(credential);
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

    /** Tests a saved credential against the agent endpoint and records the outcome (no secret returned). */
    @Transactional
    public CredentialTestResponse test(UUID actorId, UUID organizationId, UUID projectId, UUID agentId,
                                       UUID credentialId) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.ADMIN);
        AgentCredential credential = credentialRepository.findByIdAndAgentId(credentialId, agentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Agent credential", credentialId));

        CredentialConnectionTester.Result result =
                connectionTester.test(agent.getEndpointUrl(), agent.getFramework(), buildHeadersFor(credential));
        Instant testedAt = Instant.now();
        credential.recordTestResult(testedAt, result.success(), result.httpStatus(), result.message());
        log.info("Credential {} connection test for agent {}: success={} status={}",
                credentialId, agentId, result.success(), result.httpStatus());
        return new CredentialTestResponse(result.success(), result.httpStatus(), result.latencyMs(),
                result.message(), testedAt);
    }

    /** Dry-runs a connection test for an unsaved credential so a user can verify a secret before storing it. */
    @Transactional(readOnly = true)
    public CredentialTestResponse testDraft(UUID actorId, UUID organizationId, UUID projectId, UUID agentId,
                                            TestAgentCredentialRequest request) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.ADMIN);
        requireFields(request.authType(), StringUtils.hasText(request.secret()),
                StringUtils.hasText(request.username()), StringUtils.hasText(request.headerName()));
        Map<String, String> headers = assembleHeaders(request.authType(), request.secret(), request.username(),
                resolveHeaderName(request.authType(), request.headerName()),
                resolveHeaderPrefix(request.authType(), request.headerPrefix()));
        CredentialConnectionTester.Result result =
                connectionTester.test(agent.getEndpointUrl(), agent.getFramework(), headers);
        return new CredentialTestResponse(result.success(), result.httpStatus(), result.latencyMs(),
                result.message(), Instant.now());
    }

    /**
     * Produces the HTTP headers required to authenticate against the agent's
     * endpoint, decrypting the active credential. Internal use only (health
     * probes and invocation); never exposed via the API.
     */
    @Transactional(readOnly = true)
    public Map<String, String> resolveAuthHeaders(Agent agent) {
        AgentCredential credential = credentialRepository
                .findFirstByAgentIdAndActiveTrueOrderByCreatedAtDesc(agent.getId())
                .orElse(null);
        if (credential == null || credential.getAuthType() == AgentAuthType.NONE) {
            return Map.of();
        }
        return buildHeadersFor(credential);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private Map<String, String> buildHeadersFor(AgentCredential credential) {
        if (credential.getAuthType() == null || credential.getAuthType() == AgentAuthType.NONE) {
            return Map.of();
        }
        String secret = encryptionService.decrypt(credential.getEncryptedSecret());
        return assembleHeaders(credential.getAuthType(), secret, credential.getUsername(),
                credential.getHeaderName(), credential.getHeaderPrefix());
    }

    /** Builds the auth header map for a given (type, secret, ...) tuple. Pure — used for saved and draft credentials. */
    private Map<String, String> assembleHeaders(AgentAuthType type, String secret, String username,
                                                String headerName, String headerPrefix) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (type == null || type == AgentAuthType.NONE) {
            return headers;
        }
        switch (type) {
            case API_KEY -> {
                String name = StringUtils.hasText(headerName) ? headerName : DEFAULT_API_KEY_HEADER;
                headers.put(name, applyPrefix(headerPrefix, secret));
            }
            case BEARER_TOKEN -> {
                String prefix = StringUtils.hasText(headerPrefix) ? headerPrefix.trim() : DEFAULT_BEARER_PREFIX;
                headers.put(AUTHORIZATION_HEADER, prefix + " " + (secret == null ? "" : secret));
            }
            case BASIC_AUTH -> {
                String raw = (username == null ? "" : username) + ":" + (secret == null ? "" : secret);
                headers.put(AUTHORIZATION_HEADER,
                        "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
            }
            case CUSTOM_HEADER -> {
                if (StringUtils.hasText(headerName)) {
                    headers.put(headerName, applyPrefix(headerPrefix, secret));
                }
            }
            default -> { /* NONE handled above */ }
        }
        return headers;
    }

    /** {@code "Bearer" + secret} → {@code "Bearer <secret>"}; no prefix → the bare secret. */
    private String applyPrefix(String prefix, String secret) {
        String value = secret == null ? "" : secret;
        return StringUtils.hasText(prefix) ? prefix.trim() + " " + value : value;
    }

    private void requireFields(AgentAuthType type, boolean hasSecret, boolean hasUsername, boolean hasHeaderName) {
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
                if (!hasSecret || !hasUsername) {
                    throw typeMismatch("BASIC_AUTH requires both a username and a secret");
                }
            }
            case CUSTOM_HEADER -> {
                if (!hasSecret || !hasHeaderName) {
                    throw typeMismatch("CUSTOM_HEADER requires both a header name and a secret");
                }
            }
            default -> throw typeMismatch("Unsupported authentication type");
        }
    }

    private String resolveHeaderName(AgentAuthType type, String headerName) {
        return switch (type) {
            case API_KEY -> StringUtils.hasText(headerName) ? headerName.trim() : DEFAULT_API_KEY_HEADER;
            case CUSTOM_HEADER -> trimToNull(headerName);
            default -> null;
        };
    }

    private String resolveHeaderPrefix(AgentAuthType type, String headerPrefix) {
        return switch (type) {
            case API_KEY, BEARER_TOKEN, CUSTOM_HEADER -> trimToNull(headerPrefix);
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
