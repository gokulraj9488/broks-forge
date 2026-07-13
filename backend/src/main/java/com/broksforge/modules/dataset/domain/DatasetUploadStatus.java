package com.broksforge.modules.dataset.domain;

/**
 * Lifecycle of a single file-upload attempt on {@link DatasetUpload}. Modeled the same
 * way as {@code EvaluationJobStatus} / {@code AgentHealthCheck} — a persisted, pollable
 * record of an operation rather than a bare synchronous response — even though parsing
 * runs synchronously within the upload request today (see {@code DatasetService}
 * javadoc). This keeps the shape ready for a future async/queued parser without an API
 * change, mirroring the documented {@code EvaluationJobExecutor} seam.
 */
public enum DatasetUploadStatus {
    PENDING,
    PARSING,
    COMPLETED,
    /** Byte-identical content (same checksum) already produced a version for this dataset. */
    DUPLICATE,
    FAILED
}
