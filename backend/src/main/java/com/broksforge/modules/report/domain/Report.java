package com.broksforge.modules.report.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A lightweight audit record of a generated report. The rendered content itself is
 * streamed on demand and not stored (reports are always re-rendered from live data —
 * see ADR 0009); this row powers "recent reports" listings and traceability.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "reports",
        indexes = {
                @Index(name = "idx_reports_project", columnList = "project_id"),
                @Index(name = "idx_reports_org", columnList = "organization_id"),
                @Index(name = "idx_reports_target", columnList = "target_id")
        }
)
public class Report extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ReportType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 16)
    private ReportFormat format;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
}
