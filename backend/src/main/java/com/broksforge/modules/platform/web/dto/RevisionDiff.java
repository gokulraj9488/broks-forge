package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One field-level difference between two engineering revisions. */
@Schema(name = "RevisionDiff", description = "A single field difference between two revisions")
public record RevisionDiff(
        @Schema(description = "Snapshot field name") String field,
        @Schema(description = "Value in the base revision") String before,
        @Schema(description = "Value in the target revision") String after,
        @Schema(description = "added | removed | changed | unchanged") String change
) {
}
