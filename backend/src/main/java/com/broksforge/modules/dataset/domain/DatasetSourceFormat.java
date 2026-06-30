package com.broksforge.modules.dataset.domain;

/**
 * How a {@link DatasetVersion}'s items were produced. {@code MANUAL} covers items
 * supplied directly as structured JSON rows rather than an uploaded file.
 */
public enum DatasetSourceFormat {
    CSV,
    JSON,
    MANUAL
}
