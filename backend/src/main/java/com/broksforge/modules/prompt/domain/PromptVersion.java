package com.broksforge.modules.prompt.domain;

import com.broksforge.common.domain.BaseEntity;
import com.broksforge.common.persistence.JsonStringListConverter;
import com.broksforge.modules.agent.domain.LlmProvider;
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
 * An immutable snapshot of a prompt's template text. The {@code variables} list is
 * derived from the {@code {{placeholder}}} tokens in the template at creation time.
 * The template is pure data — never evaluated as code — so rendering is
 * injection-safe. An optional provider/model hint records what the prompt was
 * authored for; {@link LlmProvider} is the platform-wide provider vocabulary.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "prompt_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_prompt_versions_number", columnNames = {"prompt_id", "version_number"}),
        indexes = {
                @Index(name = "idx_prompt_versions_prompt", columnList = "prompt_id"),
                @Index(name = "idx_prompt_versions_prompt_active", columnList = "prompt_id, active")
        }
)
public class PromptVersion extends BaseEntity {

    @Column(name = "prompt_id", nullable = false)
    private UUID promptId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "template", nullable = false, columnDefinition = "text")
    private String template;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "variables", columnDefinition = "text")
    private List<String> variables = new ArrayList<>();

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 48)
    private LlmProvider provider;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "active", nullable = false)
    private boolean active = false;
}
