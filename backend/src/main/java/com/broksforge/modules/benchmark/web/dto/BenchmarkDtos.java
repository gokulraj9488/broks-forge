package com.broksforge.modules.benchmark.web.dto;

import com.broksforge.modules.benchmark.domain.BenchmarkType;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request/response records for the benchmark API, grouped to keep the small,
 * closely-related shapes in one place.
 */
public final class BenchmarkDtos {

    private BenchmarkDtos() {
    }

    @Schema(name = "BenchmarkEntryInput", description = "An evaluation job to include as a competitor")
    public record BenchmarkEntryInput(@NotNull UUID evaluationJobId, @Size(max = 160) String label) {
    }

    @Schema(name = "CreateBenchmarkRequest", description = "Create a benchmark comparison")
    public record CreateBenchmarkRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotNull BenchmarkType type,
            @Size(max = 64) String metricKey,
            @Valid @Size(max = 100) List<BenchmarkEntryInput> entries
    ) {
    }

    @Schema(name = "AddBenchmarkEntryRequest")
    public record AddBenchmarkEntryRequest(@NotNull UUID evaluationJobId, @Size(max = 160) String label) {
    }

    @Schema(name = "BenchmarkEntryResponse")
    public record BenchmarkEntryResponse(UUID id, UUID evaluationJobId, String label, Instant createdAt) {
    }

    @Schema(name = "BenchmarkResponse")
    public record BenchmarkResponse(
            UUID id,
            UUID organizationId,
            UUID projectId,
            String name,
            String description,
            UUID ownerId,
            BenchmarkType type,
            String metricKey,
            long entryCount,
            List<BenchmarkEntryResponse> entries,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    @Schema(name = "BenchmarkSummaryResponse", description = "Compact benchmark listing row")
    public record BenchmarkSummaryResponse(
            UUID id,
            String name,
            BenchmarkType type,
            String metricKey,
            long entryCount,
            Instant createdAt
    ) {
    }

    @Schema(name = "LeaderboardRow")
    public record LeaderboardRow(
            int rank,
            UUID entryId,
            String label,
            UUID evaluationJobId,
            UUID agentId,
            EvaluationStatus jobStatus,
            BigDecimal score,
            Map<String, Object> summary
    ) {
    }

    @Schema(name = "BenchmarkLeaderboardResponse")
    public record BenchmarkLeaderboardResponse(
            UUID benchmarkId,
            String name,
            BenchmarkType type,
            String metricKey,
            boolean higherIsBetter,
            List<LeaderboardRow> rankings
    ) {
    }
}
