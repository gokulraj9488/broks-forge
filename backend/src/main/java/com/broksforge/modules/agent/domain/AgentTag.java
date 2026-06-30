package com.broksforge.modules.agent.domain;

import com.broksforge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A free-form label attached to an agent for organisation and filtering. Labels
 * are normalised to lower case and are unique per agent.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "agent_tags",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_agent_tags_agent_label", columnNames = {"agent_id", "label"}),
        indexes = {
                @Index(name = "idx_agent_tags_agent", columnList = "agent_id"),
                @Index(name = "idx_agent_tags_label", columnList = "label")
        }
)
public class AgentTag extends BaseEntity {

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "label", nullable = false, length = 64)
    private String label;
}
