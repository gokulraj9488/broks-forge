package com.broksforge.modules.provider.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "EmbeddingModelsResponse", description = "Embedding models discovered for a provider")
public record EmbeddingModelsResponse(
        @Schema(description = "False when this provider type has no embeddings API at all")
        boolean supported,
        List<String> models,
        @Schema(description = "Explanation when supported is false, or models came back empty/failed")
        String message
) {
}
