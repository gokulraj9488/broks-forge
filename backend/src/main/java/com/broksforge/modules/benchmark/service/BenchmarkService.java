package com.broksforge.modules.benchmark.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.benchmark.domain.Benchmark;
import com.broksforge.modules.benchmark.domain.BenchmarkEntry;
import com.broksforge.modules.benchmark.repository.BenchmarkEntryRepository;
import com.broksforge.modules.benchmark.repository.BenchmarkRepository;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.AddBenchmarkEntryRequest;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.BenchmarkEntryInput;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.BenchmarkEntryResponse;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.BenchmarkLeaderboardResponse;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.BenchmarkResponse;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.BenchmarkSummaryResponse;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.CreateBenchmarkRequest;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.LeaderboardRow;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.service.SummaryMetrics;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Builds and ranks benchmarks. A benchmark is a thin grouping of evaluation jobs; the
 * leaderboard is computed on read from each job's summary, so it always reflects the
 * latest job state and stores no derived data that could drift.
 */
@Slf4j
@Service
public class BenchmarkService {

    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkEntryRepository entryRepository;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final EvaluationService evaluationService;

    public BenchmarkService(BenchmarkRepository benchmarkRepository,
                            BenchmarkEntryRepository entryRepository,
                            OrganizationAccessService accessService,
                            ProjectService projectService,
                            EvaluationService evaluationService) {
        this.benchmarkRepository = benchmarkRepository;
        this.entryRepository = entryRepository;
        this.accessService = accessService;
        this.projectService = projectService;
        this.evaluationService = evaluationService;
    }

    @Transactional
    public BenchmarkResponse create(UUID actorId, UUID organizationId, UUID projectId,
                                    CreateBenchmarkRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        projectService.assertProjectExists(organizationId, projectId);

        Benchmark benchmark = new Benchmark();
        benchmark.setOrganizationId(organizationId);
        benchmark.setProjectId(projectId);
        benchmark.setOwnerId(actorId);
        benchmark.setName(request.name().trim());
        benchmark.setDescription(trimToNull(request.description()));
        benchmark.setType(request.type());
        benchmark.setMetricKey(StringUtils.hasText(request.metricKey()) ? request.metricKey().trim() : "passRate");
        Benchmark saved = benchmarkRepository.save(benchmark);

        if (request.entries() != null) {
            for (BenchmarkEntryInput input : request.entries()) {
                EvaluationJobResponse job = evaluationService.get(actorId, organizationId, projectId,
                        input.evaluationJobId());
                addEntryInternal(saved, organizationId, job, input.label());
            }
        }
        log.info("Benchmark {} ('{}') created in project {} by {}", saved.getId(), saved.getName(), projectId, actorId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<BenchmarkSummaryResponse> list(UUID actorId, UUID organizationId, UUID projectId,
                                                       Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        return PageResponse.from(
                benchmarkRepository.findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(projectId, pageable),
                b -> new BenchmarkSummaryResponse(b.getId(), b.getName(), b.getType(), b.getMetricKey(),
                        entryRepository.countByBenchmarkId(b.getId()), b.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public BenchmarkResponse get(UUID actorId, UUID organizationId, UUID projectId, UUID benchmarkId) {
        accessService.requireMembership(organizationId, actorId);
        return toResponse(load(organizationId, projectId, benchmarkId));
    }

    @Transactional
    public BenchmarkResponse addEntry(UUID actorId, UUID organizationId, UUID projectId, UUID benchmarkId,
                                      AddBenchmarkEntryRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        Benchmark benchmark = load(organizationId, projectId, benchmarkId);
        EvaluationJobResponse job = evaluationService.get(actorId, organizationId, projectId,
                request.evaluationJobId());
        addEntryInternal(benchmark, organizationId, job, request.label());
        return toResponse(benchmark);
    }

    @Transactional
    public void removeEntry(UUID actorId, UUID organizationId, UUID projectId, UUID benchmarkId, UUID entryId) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        load(organizationId, projectId, benchmarkId);
        BenchmarkEntry entry = entryRepository.findByIdAndBenchmarkId(entryId, benchmarkId)
                .orElseThrow(() -> ResourceNotFoundException.of("Benchmark entry", entryId));
        entryRepository.delete(entry);
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId, UUID projectId, UUID benchmarkId) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.ADMIN);
        Benchmark benchmark = load(organizationId, projectId, benchmarkId);
        benchmark.softDelete(actorId);
        log.info("Benchmark {} soft-deleted in project {} by {}", benchmarkId, projectId, actorId);
    }

    @Transactional(readOnly = true)
    public BenchmarkLeaderboardResponse leaderboard(UUID actorId, UUID organizationId, UUID projectId,
                                                    UUID benchmarkId) {
        accessService.requireMembership(organizationId, actorId);
        Benchmark benchmark = load(organizationId, projectId, benchmarkId);
        String metricKey = benchmark.getMetricKey();
        boolean higherIsBetter = SummaryMetrics.higherIsBetter(metricKey);

        List<Scored> scored = new ArrayList<>();
        List<Scored> unscored = new ArrayList<>();
        for (BenchmarkEntry entry : entryRepository.findByBenchmarkIdOrderByCreatedAtAsc(benchmarkId)) {
            EvaluationJobResponse job = tryLoadJob(actorId, organizationId, projectId, entry.getEvaluationJobId());
            Double score = job == null ? null : SummaryMetrics.value(job.summary(), metricKey);
            Scored row = new Scored(entry, job, score);
            (score == null ? unscored : scored).add(row);
        }

        Comparator<Scored> comparator = Comparator.comparingDouble(Scored::score);
        scored.sort(higherIsBetter ? comparator.reversed() : comparator);

        List<LeaderboardRow> rankings = new ArrayList<>();
        int rank = 1;
        for (Scored s : scored) {
            rankings.add(toRow(rank++, s));
        }
        for (Scored s : unscored) {
            rankings.add(toRow(rank++, s));
        }
        return new BenchmarkLeaderboardResponse(benchmarkId, benchmark.getName(), benchmark.getType(),
                metricKey, higherIsBetter, rankings);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private void addEntryInternal(Benchmark benchmark, UUID organizationId, EvaluationJobResponse job, String label) {
        if (entryRepository.existsByBenchmarkIdAndEvaluationJobId(benchmark.getId(), job.id())) {
            throw new ResourceConflictException(ErrorCode.CONFLICT,
                    "This evaluation job is already an entry in the benchmark");
        }
        BenchmarkEntry entry = new BenchmarkEntry();
        entry.setBenchmarkId(benchmark.getId());
        entry.setOrganizationId(organizationId);
        entry.setEvaluationJobId(job.id());
        entry.setLabel(StringUtils.hasText(label) ? label.trim() : job.name());
        entryRepository.save(entry);
    }

    private EvaluationJobResponse tryLoadJob(UUID actorId, UUID organizationId, UUID projectId, UUID jobId) {
        try {
            return evaluationService.get(actorId, organizationId, projectId, jobId);
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    private LeaderboardRow toRow(int rank, Scored s) {
        BigDecimal score = s.score() == null ? null : BigDecimal.valueOf(s.score());
        return new LeaderboardRow(
                rank,
                s.entry().getId(),
                s.entry().getLabel(),
                s.entry().getEvaluationJobId(),
                s.job() == null ? null : s.job().agentId(),
                s.job() == null ? null : s.job().status(),
                score,
                s.job() == null ? java.util.Map.of() : s.job().summary());
    }

    private BenchmarkResponse toResponse(Benchmark benchmark) {
        List<BenchmarkEntryResponse> entries = entryRepository
                .findByBenchmarkIdOrderByCreatedAtAsc(benchmark.getId()).stream()
                .map(e -> new BenchmarkEntryResponse(e.getId(), e.getEvaluationJobId(), e.getLabel(), e.getCreatedAt()))
                .toList();
        return new BenchmarkResponse(
                benchmark.getId(), benchmark.getOrganizationId(), benchmark.getProjectId(), benchmark.getName(),
                benchmark.getDescription(), benchmark.getOwnerId(), benchmark.getType(), benchmark.getMetricKey(),
                entries.size(), entries, benchmark.getCreatedAt(), benchmark.getUpdatedAt());
    }

    private Benchmark load(UUID organizationId, UUID projectId, UUID benchmarkId) {
        return benchmarkRepository
                .findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(benchmarkId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Benchmark", benchmarkId));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Scored(BenchmarkEntry entry, EvaluationJobResponse job, Double score) {
    }
}
