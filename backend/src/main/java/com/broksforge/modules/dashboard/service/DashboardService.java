package com.broksforge.modules.dashboard.service;

import com.broksforge.modules.agent.service.AgentService;
import com.broksforge.modules.agent.web.dto.AgentFilter;
import com.broksforge.modules.analytics.service.AnalyticsService;
import com.broksforge.modules.dashboard.web.dto.DashboardDtos.DashboardCounts;
import com.broksforge.modules.dashboard.web.dto.DashboardDtos.DashboardResponse;
import com.broksforge.modules.dashboard.web.dto.DashboardDtos.TopAgent;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.dataset.web.dto.DatasetFilter;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.service.SummaryMetrics;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobFilter;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.prompt.service.PromptService;
import com.broksforge.modules.prompt.web.dto.PromptFilter;
import com.broksforge.modules.benchmark.service.BenchmarkService;
import com.broksforge.modules.regression.service.RegressionService;
import com.broksforge.modules.report.service.ReportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only composition of the project dashboard: counts, recent activity, analytics,
 * recent reports, regression alerts and top agents. Every figure is sourced through a
 * module's published service, so tenant/project scoping is enforced once, in each
 * owning module.
 */
@Service
public class DashboardService {

    private static final int TOP_AGENTS = 5;

    private final AgentService agentService;
    private final DatasetService datasetService;
    private final PromptService promptService;
    private final EvaluationService evaluationService;
    private final BenchmarkService benchmarkService;
    private final AnalyticsService analyticsService;
    private final ReportService reportService;
    private final RegressionService regressionService;

    public DashboardService(AgentService agentService,
                            DatasetService datasetService,
                            PromptService promptService,
                            EvaluationService evaluationService,
                            BenchmarkService benchmarkService,
                            AnalyticsService analyticsService,
                            ReportService reportService,
                            RegressionService regressionService) {
        this.agentService = agentService;
        this.datasetService = datasetService;
        this.promptService = promptService;
        this.evaluationService = evaluationService;
        this.benchmarkService = benchmarkService;
        this.analyticsService = analyticsService;
        this.reportService = reportService;
        this.regressionService = regressionService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse overview(UUID actorId, UUID organizationId, UUID projectId) {
        PageRequest one = PageRequest.of(0, 1);

        long agents = agentService.search(actorId, organizationId, projectId,
                new AgentFilter(null, null, null, null, null, null, null), one).totalElements();
        long datasets = datasetService.search(actorId, organizationId, projectId,
                new DatasetFilter(null, null, null, null), one).totalElements();
        long prompts = promptService.search(actorId, organizationId, projectId,
                new PromptFilter(null, null, null), one).totalElements();
        long evaluationJobs = evaluationService.search(actorId, organizationId, projectId,
                new EvaluationJobFilter(null, null, null, null), one).totalElements();
        long runningJobs = evaluationService.search(actorId, organizationId, projectId,
                new EvaluationJobFilter(null, EvaluationStatus.RUNNING, null, null), one).totalElements();
        long benchmarks = benchmarkService.list(actorId, organizationId, projectId, one).totalElements();

        DashboardCounts counts = new DashboardCounts(agents, datasets, prompts, evaluationJobs, runningJobs, benchmarks);

        List<EvaluationJobResponse> recentDetailed =
                evaluationService.recentDetailed(actorId, organizationId, projectId);
        List<EvaluationJobResponse> recentJobs = recentDetailed.stream().limit(8).toList();

        return new DashboardResponse(
                counts,
                recentJobs,
                analyticsService.overview(actorId, organizationId, projectId, 30),
                reportService.recent(actorId, organizationId, projectId),
                regressionService.recentRegressed(actorId, organizationId, projectId),
                topAgents(recentDetailed));
    }

    private List<TopAgent> topAgents(List<EvaluationJobResponse> jobs) {
        Map<UUID, double[]> accumulator = new LinkedHashMap<>();
        for (EvaluationJobResponse job : jobs) {
            if (job.status() != EvaluationStatus.COMPLETED) {
                continue;
            }
            Double passRate = SummaryMetrics.value(job.summary(), "passRate");
            if (passRate == null) {
                continue;
            }
            double[] acc = accumulator.computeIfAbsent(job.agentId(), k -> new double[2]);
            acc[0] += passRate;
            acc[1] += 1;
        }
        List<TopAgent> top = new ArrayList<>();
        accumulator.forEach((agentId, acc) ->
                top.add(new TopAgent(agentId, round(acc[0] / acc[1]), (int) acc[1])));
        top.sort(Comparator.comparingDouble(TopAgent::avgPassRate).reversed());
        return top.stream().limit(TOP_AGENTS).toList();
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
