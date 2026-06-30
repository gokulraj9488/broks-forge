package com.broksforge.modules.dataset.service;

import java.util.UUID;

/**
 * Published reference to a resolved dataset version: the pinned version id, its
 * number, and item count. Used by evaluation to validate and pin inputs at job
 * creation time.
 */
public record DatasetVersionRef(UUID versionId, int versionNumber, int itemCount) {
}
