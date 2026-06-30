package com.broksforge.modules.report.service;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.benchmark.service.BenchmarkService;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.BenchmarkLeaderboardResponse;
import com.broksforge.modules.benchmark.web.dto.BenchmarkDtos.LeaderboardRow;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationRunResponse;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import com.broksforge.modules.regression.service.RegressionService;
import com.broksforge.modules.regression.web.dto.RegressionDtos.RegressionCheckResponse;
import com.broksforge.modules.report.domain.Report;
import com.broksforge.modules.report.domain.ReportType;
import com.broksforge.modules.report.repository.ReportRepository;
import com.broksforge.modules.report.web.dto.ReportDtos.GenerateReportRequest;
import com.broksforge.modules.report.web.dto.ReportDtos.ReportResponse;
import com.broksforge.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates downloadable reports on demand and records a lightweight audit row for
 * "recent reports". Reports are always re-rendered from live data, never stored as
 * blobs (see ADR 0009).
 */
@Slf4j
@Service
public class ReportService {

    private static final int MAX_EXPORT_RUNS = 5000;

    private final ReportRepository reportRepository;
    private final OrganizationAccessService accessService;
    private final ProjectService projectService;
    private final EvaluationService evaluationService;
    private final BenchmarkService benchmarkService;
    private final RegressionService regressionService;
    private final ReportRenderer renderer;

    public ReportService(ReportRepository reportRepository,
                         OrganizationAccessService accessService,
                         ProjectService projectService,
                         EvaluationService evaluationService,
                         BenchmarkService benchmarkService,
                         RegressionService regressionService,
                         ReportRenderer renderer) {
        this.reportRepository = reportRepository;
        this.accessService = accessService;
        this.projectService = projectService;
        this.evaluationService = evaluationService;
        this.benchmarkService = benchmarkService;
        this.regressionService = regressionService;
        this.renderer = renderer;
    }

    @Transactional
    public RenderedReport generate(UUID actorId, UUID organizationId, UUID projectId, GenerateReportRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.MEMBER);
        projectService.assertProjectExists(organizationId, projectId);

        ReportData data = switch (request.type()) {
            case EVALUATION_JOB -> evaluationJobData(actorId, organizationId, projectId, request.targetId());
            case BENCHMARK -> benchmarkData(actorId, organizationId, projectId, request.targetId());
            case REGRESSION -> regressionData(actorId, organizationId, projectId, request.targetId());
        };

        String name = StringUtils.hasText(request.name()) ? request.name().trim() : data.title();
        RenderedReport rendered = renderer.render(request.format(), name, data);

        Report report = new Report();
        report.setOrganizationId(organizationId);
        report.setProjectId(projectId);
        report.setName(name);
        report.setType(request.type());
        report.setFormat(request.format());
        report.setTargetId(request.targetId());
        report.setOwnerId(actorId);
        reportRepository.save(report);

        log.info("Report ({} {}) generated for target {} in project {} by {}",
                request.type(), request.format(), request.targetId(), projectId, actorId);
        return rendered;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> list(UUID actorId, UUID organizationId, UUID projectId, Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        projectService.assertProjectExists(organizationId, projectId);
        return PageResponse.from(
                reportRepository.findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(projectId, pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReportResponse get(UUID actorId, UUID organizationId, UUID projectId, UUID reportId) {
        accessService.requireMembership(organizationId, actorId);
        Report report = reportRepository
                .findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(reportId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Report", reportId));
        return toResponse(report);
    }

    /** Published for the dashboard: the most recent reports in a project. */
    @Transactional(readOnly = true)
    public List<ReportResponse> recent(UUID actorId, UUID organizationId, UUID projectId) {
        accessService.requireMembership(organizationId, actorId);
        return reportRepository.findTop10ByProjectIdAndDeletedFalseOrderByCreatedAtDesc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ----------------------------------------------------------------------
    // Report data assembly
    // ----------------------------------------------------------------------

    private ReportData evaluationJobData(UUID actorId, UUID organizationId, UUID projectId, UUID jobId) {
        EvaluationJobResponse job = evaluationService.get(actorId, organizationId, projectId, jobId);
        List<EvaluationRunResponse> runs = evaluationService
                .listRuns(actorId, organizationId, projectId, jobId, PageRequest.of(0, MAX_EXPORT_RUNS)).content();

        List<String> headers = List.of("sequence", "status", "latencyMs", "totalTokens", "cost",
                "passed", "score", "httpStatus", "input", "output");
        List<List<String>> rows = new ArrayList<>(runs.size());
        for (EvaluationRunResponse run : runs) {
            rows.add(List.of(
                    str(run.sequence()), run.status().name(), str(run.latencyMs()), str(run.totalTokens()),
                    str(run.cost()), str(run.passed()), str(run.score()), str(run.httpStatus()),
                    nz(run.input()), nz(run.output())));
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("job", job);
        model.put("runs", runs);
        return new ReportData("Evaluation report — " + job.name(), model, headers, rows);
    }

    private ReportData benchmarkData(UUID actorId, UUID organizationId, UUID projectId, UUID benchmarkId) {
        BenchmarkLeaderboardResponse leaderboard =
                benchmarkService.leaderboard(actorId, organizationId, projectId, benchmarkId);
        List<String> headers = List.of("rank", "label", "evaluationJobId", "score", "jobStatus");
        List<List<String>> rows = new ArrayList<>();
        for (LeaderboardRow row : leaderboard.rankings()) {
            rows.add(List.of(
                    str(row.rank()), nz(row.label()), str(row.evaluationJobId()), str(row.score()),
                    row.jobStatus() == null ? "" : row.jobStatus().name()));
        }
        return new ReportData("Benchmark report — " + leaderboard.name(), leaderboard, headers, rows);
    }

    private ReportData regressionData(UUID actorId, UUID organizationId, UUID projectId, UUID checkId) {
        RegressionCheckResponse check = regressionService.get(actorId, organizationId, projectId, checkId);
        List<String> headers = List.of("dimension", "baseline", "candidate", "deltaPct", "regressed");
        List<List<String>> rows = new ArrayList<>();
        check.findings().forEach((key, value) -> {
            if (value instanceof Map<?, ?> finding) {
                rows.add(List.of(
                        str(finding.getOrDefault("label", key)),
                        str(finding.get("baseline")),
                        str(finding.get("candidate")),
                        str(finding.get("deltaPct")),
                        str(finding.get("regressed"))));
            }
        });
        return new ReportData("Regression report — " + check.name(), check, headers, rows);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private ReportResponse toResponse(Report report) {
        return new ReportResponse(report.getId(), report.getName(), report.getType(), report.getFormat(),
                report.getTargetId(), report.getOwnerId(), report.getCreatedAt());
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }
}
