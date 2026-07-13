package com.broksforge.modules.dataset.domain;

/**
 * How a {@link DatasetVersion}'s items were produced, and (shared enum) what a
 * {@code DatasetUpload} was submitted as. {@code MANUAL} covers items supplied directly
 * as structured JSON rows rather than an uploaded file. {@code ZIP} only ever appears on
 * a {@code DatasetUpload} — it is unwrapped to the single CSV/JSON/XLSX entry it contains
 * before parsing, so a resulting {@code DatasetVersion.sourceFormat} is always the
 * unwrapped format, never {@code ZIP}.
 */
public enum DatasetSourceFormat {
    CSV,
    JSON,
    MANUAL,
    XLSX,
    ZIP
}
