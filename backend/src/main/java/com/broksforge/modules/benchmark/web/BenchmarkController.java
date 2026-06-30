package com.broksforge.modules.benchmark.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.benchmark.service.BenchmarkService;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.AddBenchmarkEntryRequest;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.BenchmarkLeaderboardResponse;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.BenchmarkResponse;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.BenchmarkSummaryResponse;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.CreateBenchmarkRequest;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Benchmarks: compare evaluation jobs and render a ranked leaderboard.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/benchmarks")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Benchmarks", description = "Compare evaluation jobs and produce leaderboards")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @PostMapping
    @Operation(summary = "Create a benchmark")
    public ResponseEntity<BenchmarkResponse> create(@PathVariable UUID organizationId,
                                                    @PathVariable UUID projectId,
                                                    @Valid @RequestBody CreateBenchmarkRequest request) {
        BenchmarkResponse response =
                benchmarkService.create(SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List benchmarks")
    public ResponseEntity<PageResponse<BenchmarkSummaryResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(benchmarkService.list(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, pageable));
    }

    @GetMapping("/{benchmarkId}")
    @Operation(summary = "Get a benchmark with its entries")
    public ResponseEntity<BenchmarkResponse> get(@PathVariable UUID organizationId,
                                                 @PathVariable UUID projectId,
                                                 @PathVariable UUID benchmarkId) {
        return ResponseEntity.ok(benchmarkService.get(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, benchmarkId));
    }

    @GetMapping("/{benchmarkId}/leaderboard")
    @Operation(summary = "Compute the benchmark leaderboard")
    public ResponseEntity<BenchmarkLeaderboardResponse> leaderboard(@PathVariable UUID organizationId,
                                                                    @PathVariable UUID projectId,
                                                                    @PathVariable UUID benchmarkId) {
        return ResponseEntity.ok(benchmarkService.leaderboard(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, benchmarkId));
    }

    @PostMapping("/{benchmarkId}/entries")
    @Operation(summary = "Add an evaluation job to a benchmark")
    public ResponseEntity<BenchmarkResponse> addEntry(@PathVariable UUID organizationId,
                                                      @PathVariable UUID projectId,
                                                      @PathVariable UUID benchmarkId,
                                                      @Valid @RequestBody AddBenchmarkEntryRequest request) {
        return ResponseEntity.ok(benchmarkService.addEntry(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, benchmarkId, request));
    }

    @DeleteMapping("/{benchmarkId}/entries/{entryId}")
    @Operation(summary = "Remove an entry from a benchmark")
    public ResponseEntity<Void> removeEntry(@PathVariable UUID organizationId,
                                            @PathVariable UUID projectId,
                                            @PathVariable UUID benchmarkId,
                                            @PathVariable UUID entryId) {
        benchmarkService.removeEntry(SecurityUtils.requireCurrentUserId(), organizationId, projectId,
                benchmarkId, entryId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{benchmarkId}")
    @Operation(summary = "Delete a benchmark", description = "Soft-deletes the benchmark. Requires ADMIN.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID benchmarkId) {
        benchmarkService.delete(SecurityUtils.requireCurrentUserId(), organizationId, projectId, benchmarkId);
        return ResponseEntity.noContent().build();
    }
}
