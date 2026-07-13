package com.broksforge.modules.agent.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
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
import java.util.UUID;

/**
 * The central platform entity: a registered AI agent (a complete AI application),
 * independent of the framework or language it is built with.
 *
 * <p>An {@code Agent} is the aggregate root that future modules — evaluation,
 * benchmarking, prompt management, tracing — attach to by {@code id}. Its
 * versions, credentials, health checks and tags are modelled as separate
 * entities referencing this agent, keeping the aggregate cohesive while
 * preserving clean module boundaries.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
// (project_id, slug) uniqueness is enforced by a PARTIAL unique index
// (WHERE deleted = false) defined in migration V30, so a slug frees up once its
// agent is soft-deleted. JPA's @UniqueConstraint cannot express a partial index,
// so it is intentionally omitted here — the migration is the source of truth.
@Table(
        name = "agents",
        indexes = {
                @Index(name = "idx_agents_project", columnList = "project_id"),
                @Index(name = "idx_agents_org", columnList = "organization_id"),
                @Index(name = "idx_agents_owner", columnList = "owner_id"),
                @Index(name = "idx_agents_framework", columnList = "framework"),
                @Index(name = "idx_agents_status", columnList = "status"),
                @Index(name = "idx_agents_health_status", columnList = "health_status")
        }
)
public class Agent extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private AgentVisibility visibility = AgentVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "framework", nullable = false, length = 48)
    private AgentFramework framework;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 32)
    private AgentLanguage language;

    @Column(name = "endpoint_url", nullable = false, length = 2048)
    private String endpointUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 32)
    private AgentAuthType authType = AgentAuthType.NONE;

    @Column(name = "current_active_version_id")
    private UUID currentActiveVersionId;

    /**
     * Optional link to a {@code Provider} (Provider abstraction milestone): when set, this
     * agent inherits the provider's base URL, default model, headers and capabilities unless
     * it sets its own {@link #modelOverride}/{@link #endpointOverride}. Inheritance is resolved
     * once, at write time (see {@code ProviderService}/{@code AgentService}) — {@link #endpointUrl}
     * always holds the effective, already-resolved value, so every existing consumer
     * ({@code AgentEndpointInvoker}, {@code HealthProbePlanner}, evaluation execution) keeps
     * reading it exactly as before. {@code null} for agents not linked to a provider — fully
     * backward compatible with every agent registered before this milestone.
     */
    @Column(name = "provider_id")
    private UUID providerId;

    /** Agent-level model override when linked to a {@link #providerId}; null inherits the provider's default. */
    @Column(name = "model_override", length = 128)
    private String modelOverride;

    /** Agent-level endpoint override when linked to a {@link #providerId}; null inherits the provider's base URL. */
    @Column(name = "endpoint_override", length = 2048)
    private String endpointOverride;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 32)
    private AgentHealthStatus healthStatus = AgentHealthStatus.UNKNOWN;

    @Column(name = "last_health_check_at")
    private Instant lastHealthCheckAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentStatus status = AgentStatus.ACTIVE;

    @Embedded
    private AgentCapabilities capabilities = new AgentCapabilities();

    public boolean isArchived() {
        return status == AgentStatus.ARCHIVED;
    }

    public void archive() {
        this.status = AgentStatus.ARCHIVED;
    }

    public void unarchive() {
        this.status = AgentStatus.ACTIVE;
    }

    public void applyHealth(AgentHealthStatus newStatus, Instant checkedAt) {
        this.healthStatus = newStatus;
        this.lastHealthCheckAt = checkedAt;
    }
}
