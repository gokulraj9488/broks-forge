package com.broksforge.modules.dataset.web.dto;

import com.broksforge.modules.dataset.domain.DatasetSourceFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Imports rows as a new immutable dataset version from inline CSV or JSON content.
 *
 * <ul>
 *   <li><b>CSV</b>: the first row is the header. {@code inputField} / {@code expectedOutputField}
 *       select columns by name; remaining columns become item metadata.</li>
 *   <li><b>JSON</b>: an array of objects. {@code inputField} / {@code expectedOutputField}
 *       select keys; remaining keys become item metadata.</li>
 * </ul>
 *
 * When the field names are omitted, sensible defaults are used ({@code input} and
 * {@code expected_output}/{@code output}).
 */
@Schema(name = "ImportDatasetRequest", description = "Import a new dataset version from CSV or JSON")
public record ImportDatasetRequest(
        @NotNull DatasetSourceFormat format,
        @NotBlank @Size(max = 5_000_000) String content,
        @Size(max = 1000) String description,
        @Size(max = 128) String inputField,
        @Size(max = 128) String expectedOutputField
) {
}
