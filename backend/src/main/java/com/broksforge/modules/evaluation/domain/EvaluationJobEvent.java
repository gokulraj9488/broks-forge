package com.broksforge.modules.evaluation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single, immutable audit-trail entry for an {@link EvaluationJob}'s execution
 * engine — deliberately append-only and independent of {@link BaseEntity} (no
 * version/soft-delete semantics apply to a log record).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "evaluation_job_events",
        indexes = @Index(name = "idx_eval_job_events_job", columnList = "evaluation_job_id, created_at")
)
public class EvaluationJobEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "evaluation_job_id", nullable = false)
    private UUID evaluationJobId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EvaluationJobEventType eventType;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
