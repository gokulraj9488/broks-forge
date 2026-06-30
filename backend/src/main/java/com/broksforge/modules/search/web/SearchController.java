package com.broksforge.modules.search.web;

import com.broksforge.modules.search.service.SearchService;
import com.broksforge.modules.search.web.dto.SearchDtos.SearchResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Global search across a project's agents, datasets, prompts and evaluation jobs.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/search")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Search", description = "Global project search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(summary = "Search across resource types")
    public ResponseEntity<SearchResponse> search(@PathVariable UUID organizationId,
                                                 @PathVariable UUID projectId,
                                                 @RequestParam String q,
                                                 @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(searchService.search(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, q, limit));
    }
}
