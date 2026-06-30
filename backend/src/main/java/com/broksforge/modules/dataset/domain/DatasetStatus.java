package com.broksforge.modules.dataset.domain;

/**
 * Lifecycle state of a dataset. Archived datasets remain readable and usable by
 * historical evaluation jobs but are hidden from default listings and rejected
 * for new mutations until unarchived.
 */
public enum DatasetStatus {
    ACTIVE,
    ARCHIVED
}
