package com.broksforge.modules.dataset.domain;

/**
 * Who may discover a dataset. Enforcement is project/organization scoped; this is
 * the forward-compatibility seam for a future cross-organization dataset catalog.
 */
public enum DatasetVisibility {
    PRIVATE,
    ORGANIZATION,
    PUBLIC
}
