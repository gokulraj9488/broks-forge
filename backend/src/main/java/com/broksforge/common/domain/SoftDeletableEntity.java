package com.broksforge.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for aggregates that support soft deletion.
 *
 * <p>Rather than removing rows, callers set {@link #deleted} to {@code true}
 * and stamp {@link #deletedAt} / {@link #deletedBy}. Repositories filter out
 * soft-deleted rows by convention (see each module's repository queries).</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    /**
     * Marks this aggregate as soft-deleted.
     *
     * @param actorId the id of the user performing the deletion (may be {@code null})
     */
    public void softDelete(UUID actorId) {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = actorId;
    }
}
