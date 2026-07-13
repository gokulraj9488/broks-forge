package com.broksforge.modules.provider.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
import com.broksforge.common.persistence.JsonMetadataConverter;
import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.domain.AgentCapabilities;
import com.broksforge.modules.agent.domain.AgentHealthStatus;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.provider.persistence.JsonStringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A registered LLM provider configuration: the shared connection, authentication and
 * capability profile that agents reference instead of duplicating (Provider abstraction
 * milestone). An {@link com.broksforge.modules.agent.domain.Agent} links to a {@code Provider}
 * by {@link #getId()} and inherits its base URL, default model, headers and capabilities
 * unless it sets its own override — see {@code Agent.providerId/modelOverride/endpointOverride}.
 *
 * <p>{@link #type} reuses the existing {@link LlmProvider} enum (no parallel taxonomy) and
 * {@link #authType} reuses {@link AgentAuthType} — the same vocabulary agents already use for
 * their own (still-supported) standalone credentials, so provider-linked and unlinked agents
 * are authenticated the exact same way at the wire level.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "providers",
        indexes = {
                @Index(name = "idx_providers_project", columnList = "project_id"),
                @Index(name = "idx_providers_org", columnList = "organization_id"),
                @Index(name = "idx_providers_type", columnList = "type")
        }
)
public class Provider extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 48)
    private LlmProvider type;

    @Column(name = "base_url", nullable = false, length = 2048)
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 32)
    private AgentAuthType authType = AgentAuthType.NONE;

    /** AES-256-GCM ciphertext (see {@code CredentialEncryptionService}); never plaintext. */
    @Column(name = "encrypted_api_key", columnDefinition = "text")
    private String encryptedApiKey;

    @Column(name = "api_key_hint", length = 16)
    private String apiKeyHint;

    @Column(name = "key_version")
    private Integer keyVersion;

    @Convert(converter = JsonMetadataConverter.class)
    @Column(name = "default_headers", columnDefinition = "text")
    private Map<String, Object> defaultHeaders = new LinkedHashMap<>();

    @Column(name = "default_model", length = 128)
    private String defaultModel;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "supported_models", columnDefinition = "text")
    private List<String> supportedModels = new java.util.ArrayList<>();

    @Embedded
    private AgentCapabilities capabilities = new AgentCapabilities();

    @Convert(converter = JsonMetadataConverter.class)
    @Column(name = "rate_limits", columnDefinition = "text")
    private Map<String, Object> rateLimits = new LinkedHashMap<>();

    @Convert(converter = JsonMetadataConverter.class)
    @Column(name = "pricing_metadata", columnDefinition = "text")
    private Map<String, Object> pricingMetadata = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 32)
    private AgentHealthStatus healthStatus = AgentHealthStatus.UNKNOWN;

    @Column(name = "last_health_check_at")
    private Instant lastHealthCheckAt;

    /** Whether this provider may currently be used for new invocations (see {@code AgentInvocationService}). */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Timestamp of the most recent invocation through an agent linked to this provider, if any. */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public void applyHealth(AgentHealthStatus newStatus, Instant checkedAt) {
        this.healthStatus = newStatus;
        this.lastHealthCheckAt = checkedAt;
    }

    public void recordUsage(Instant usedAt) {
        this.lastUsedAt = usedAt;
    }
}
