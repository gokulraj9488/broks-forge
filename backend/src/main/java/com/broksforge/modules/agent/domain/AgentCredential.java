package com.broksforge.modules.agent.domain;

import com.broksforge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Authentication material the platform uses to call an agent's endpoint.
 *
 * <p>The secret value is stored <strong>encrypted</strong> (AES-256-GCM via
 * {@code CredentialEncryptionService}), never in plaintext and never hashed —
 * because the platform must retrieve and present it when calling the agent.
 * Only non-sensitive metadata (auth type, username, header name, a short hint)
 * is ever exposed through the API.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "agent_credentials",
        indexes = {
                @Index(name = "idx_agent_credentials_agent", columnList = "agent_id"),
                @Index(name = "idx_agent_credentials_agent_active", columnList = "agent_id, active")
        }
)
public class AgentCredential extends BaseEntity {

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 32)
    private AgentAuthType authType;

    /** Username for BASIC_AUTH (not secret). */
    @Column(name = "username", length = 256)
    private String username;

    /** Header name for API_KEY / CUSTOM_HEADER (not secret). */
    @Column(name = "header_name", length = 128)
    private String headerName;

    /** AES-256-GCM ciphertext of the secret. Never exposed. */
    @Column(name = "encrypted_secret", columnDefinition = "text")
    private String encryptedSecret;

    /** Short, masked hint (e.g. last 4 chars) for safe display. */
    @Column(name = "secret_hint", length = 16)
    private String secretHint;

    /** Encryption key version used, enabling key rotation. */
    @Column(name = "key_version", nullable = false)
    private int keyVersion = 1;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public void deactivate() {
        this.active = false;
    }
}
