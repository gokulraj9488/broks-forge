package com.broksforge.modules.prompt.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
import com.broksforge.common.persistence.JsonStringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A prompt-library entry: a named, reusable prompt within a project. The prompt is
 * a stable container; its actual text lives in immutable {@link PromptVersion}
 * snapshots, with exactly one version active at a time (see ADR 0008). Activation
 * and rollback move the active pointer between versions without ever editing one.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "prompts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_prompts_project_slug", columnNames = {"project_id", "slug"}),
        indexes = {
                @Index(name = "idx_prompts_project", columnList = "project_id"),
                @Index(name = "idx_prompts_org", columnList = "organization_id"),
                @Index(name = "idx_prompts_owner", columnList = "owner_id"),
                @Index(name = "idx_prompts_status", columnList = "status")
        }
)
public class Prompt extends SoftDeletableEntity {

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
    @Column(name = "status", nullable = false, length = 32)
    private PromptStatus status = PromptStatus.ACTIVE;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "tags", columnDefinition = "text")
    private List<String> tags = new ArrayList<>();

    @Column(name = "latest_version_number", nullable = false)
    private int latestVersionNumber = 0;

    @Column(name = "current_active_version_id")
    private UUID currentActiveVersionId;

    public boolean isArchived() {
        return status == PromptStatus.ARCHIVED;
    }

    public void archive() {
        this.status = PromptStatus.ARCHIVED;
    }

    public void unarchive() {
        this.status = PromptStatus.ACTIVE;
    }
}
