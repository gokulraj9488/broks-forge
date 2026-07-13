package com.broksforge.modules.rootcause.service;

import com.broksforge.config.properties.AdvisorProperties;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.service.MetricExecutionFailureTally;
import com.broksforge.modules.evaluation.service.MetricFailureTally;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationRunResponse;
import com.broksforge.modules.knowledge.service.KnowledgeGraphService;
import com.broksforge.modules.regression.service.RegressionService;
import com.broksforge.modules.regression.web.dto.RegressionDtos.RegressionCheckResponse;
import com.broksforge.modules.rootcause.web.dto.RootCauseDtos.RootCauseFindingResponse;
import com.broksforge.modules.rootcause.web.dto.RootCauseDtos.RootCauseReportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Root-cause analysis (ADR 0012): explains <em>why</em> an evaluation failed or a
 * regression occurred. Loads the evidence through the evaluation and regression
 * modules' published services (which enforce tenant scoping), runs the pure
 * {@link RootCauseEngine}, and feeds observed patterns back into the knowledge graph.
 */
@Service
public class RootCauseService {

    private final EvaluationService evaluationService;
    private final RegressionService regressionService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final RootCauseEngine engine;
    private final AdvisorProperties properties;

    public RootCauseService(EvaluationService evaluationService,
                            RegressionService regressionService,
                            KnowledgeGraphService knowledgeGraphService,
                            RootCauseEngine engine,
                            AdvisorProperties properties) {
        this.evaluationService = evaluationService;
        this.regressionService = regressionService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.engine = engine;
        this.properties = properties;
    }

    @Transactional
    public RootCauseReportResponse analyzeJob(UUID actorId, UUID organizationId, UUID projectId, UUID jobId) {
        EvaluationJobResponse job = evaluationService.get(actorId, organizationId, projectId, jobId);
        List<MetricFailureTally> tallies =
                evaluationService.metricFailureBreakdown(actorId, organizationId, projectId, jobId);
        List<MetricExecutionFailureTally> executionFailures =
                evaluationService.metricExecutionFailureBreakdown(actorId, organizationId, projectId, jobId);
        List<EvaluationRunResponse> failedRuns = evaluationService.sampleFailedRuns(
                actorId, organizationId, projectId, jobId, properties.failureSampleSize());

        List<RootCauseFinding> findings = engine.analyzeJob(job, tallies, executionFailures, failedRuns);

        List<String> notes = new ArrayList<>();
        notes.add("Analysed job \"%s\" (%d failed item(s) sampled).".formatted(job.name(), failedRuns.size()));
        if (tallies.isEmpty() && failedRuns.isEmpty()) {
            notes.add("No metric results or failed runs were available to analyse.");
        }
        return buildReport("JOB", job.name(), findings, notes);
    }

    @Transactional
    public RootCauseReportResponse analyzeRegression(UUID actorId, UUID organizationId, UUID projectId, UUID checkId) {
        RegressionCheckResponse check = regressionService.get(actorId, organizationId, projectId, checkId);
        List<RootCauseFinding> findings = engine.analyzeRegression(check);
        List<String> notes = List.of("Analysed regression check \"%s\".".formatted(check.name()));
        return buildReport("REGRESSION", check.name(), findings, notes);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private RootCauseReportResponse buildReport(String scope, String subject, List<RootCauseFinding> findings,
                                                List<String> notes) {
        List<RootCauseFindingResponse> responses = new ArrayList<>(findings.size());
        for (RootCauseFinding finding : findings) {
            knowledgeGraphService.recordObservation(finding.knowledgeKey()); // learning seam
            responses.add(new RootCauseFindingResponse(
                    finding.rootCause(), finding.severity(), finding.confidence(), finding.evidence(),
                    finding.recommendation(), finding.expectedImprovement(), finding.knowledgeKey()));
        }
        return new RootCauseReportResponse(scope, subject, responses.size(), responses, notes);
    }
}
