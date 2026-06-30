package com.broksforge.modules.dataset.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.UUID;

@Schema(name = "DatasetItemResponse", description = "A single dataset row")
public record DatasetItemResponse(
        UUID id,
        int sequence,
        String input,
        String expectedOutput,
        Map<String, Object> metadata
) {
}
