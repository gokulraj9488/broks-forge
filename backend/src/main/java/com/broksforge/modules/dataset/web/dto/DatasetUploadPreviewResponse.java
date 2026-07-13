package com.broksforge.modules.dataset.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * A dry-run over an uploaded file: its columns, an auto-detected input/expected-output mapping,
 * every column that could plausibly be either (so the caller can tell a confident guess from an
 * ambiguous one), and a peek at the data. Nothing is persisted — {@code POST .../uploads} is the
 * call that actually creates a version, using whichever mapping the caller confirms.
 */
@Schema(name = "DatasetUploadPreviewResponse", description = "Auto-detected column mapping + a data preview, before import")
public record DatasetUploadPreviewResponse(
        List<String> columns,
        String suggestedInputField,
        String suggestedExpectedOutputField,
        List<String> inputCandidates,
        List<String> expectedOutputCandidates,
        @Schema(description = "True when the input/expected-output mapping could not be confidently "
                + "auto-detected (zero or multiple candidate columns) and should be confirmed by the caller")
        boolean ambiguous,
        @Schema(description = "The first up to 5 data rows, keyed by column name")
        List<Map<String, String>> previewRows,
        int totalRows
) {
}
