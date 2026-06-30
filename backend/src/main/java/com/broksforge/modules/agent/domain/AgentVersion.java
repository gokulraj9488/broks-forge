package com.broksforge.modules.agent.domain;

import com.broksforge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable record of a single agent deployment. Versions are never mutated
 * after creation except for their {@code active} flag, which the activation /
 * rollback flow toggles. Exactly one version per agent is active at a time.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "agent_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_agent_versions_agent_number", columnNames = {"agent_id", "version_number"}),
        indexes = {
                @Index(name = "idx_agent_versions_agent", columnList = "agent_id"),
                @Index(name = "idx_agent_versions_agent_active", columnList = "agent_id, active")
        }
)
public class AgentVersion extends BaseEntity {

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "version_number", nullable = false, length = 64)
    private String versionNumber;

    /** Monotonically increasing per agent; drives default ordering. */
    @Column(name = "sequence", nullable = false)
    private long sequence;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 48)
    private LlmProvider provider;

    @Column(name = "framework_version", length = 64)
    private String frameworkVersion;

    @Column(name = "git_commit_sha", length = 64)
    private String gitCommitSha;

    /** Forward reference (by id/string) to the future Prompt Management module. */
    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 32)
    private DeploymentEnvironment environment;

    @Column(name = "release_notes", length = 2000)
    private String releaseNotes;

    @Column(name = "deployment_timestamp", nullable = false)
    private Instant deploymentTimestamp;

    @Column(name = "active", nullable = false)
    private boolean active = false;

    @Column(name = "rollback_ready", nullable = false)
    private boolean rollbackReady = true;
}
