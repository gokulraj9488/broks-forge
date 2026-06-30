package com.broksforge.modules.search.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public final class SearchDtos {

    private SearchDtos() {
    }

    @Schema(name = "SearchHit", description = "A single global-search result")
    public record SearchHit(String type, UUID id, String title, String subtitle) {
    }

    @Schema(name = "SearchResponse", description = "Global search results across resource types")
    public record SearchResponse(String query, List<SearchHit> hits) {
    }
}
