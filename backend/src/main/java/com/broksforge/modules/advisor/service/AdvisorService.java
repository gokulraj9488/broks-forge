package com.broksforge.modules.advisor.service;

import com.broksforge.modules.advisor.domain.Recommendation;
import com.broksforge.modules.advisor.domain.Severity;
import com.broksforge.modules.advisor.web.dto.AdvisorDtos.AdvisoryReportResponse;
import com.broksforge.modules.advisor.web.dto.AdvisorDtos.RecommendationResponse;
import com.broksforge.modules.advisor.web.dto.AdvisorDtos.SeverityCount;
import com.broksforge.modules.agent.web.dto.AgentResponse;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.knowledge.service.KnowledgeGraphService;
import com.broksforge.modules.prompt.web.dto.PromptVersionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The AI Engineering Advisor (ADR 0011). Composes the per-domain advisors over data
 * read through other modules' published services, turning observed signals into
 * ranked, actionable recommendations. Recommendations are computed on read and never
 * persisted, so they always reflect current state.
 *
 * <p>Tenant scoping and authorization are enforced by the published services this
 * composes (evaluation, agent, prompt), exactly as the dashboard does. As advice is
 * produced, observed patterns are fed back into the knowledge graph's occurrence
 * counters — the learning seam (ADR 0013).</p>
 */
@Service
public class AdvisorService {

    private final EvaluationService evaluationService;
    private final com.broksforge.modules.agent.service.AgentService agentService;
    private final com.broksforge.modules.prompt.service.PromptService promptService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final ModelAdvisor modelAdvisor;
    private final CostAdvisor costAdvisor;
    private final AgentAdvisor agentAdvisor;
    private final RagAdvisor ragAdvisor;
    private final PromptAdvisor promptAdvisor;

    public AdvisorService(EvaluationService evaluationService,
                          com.broksforge.modules.agent.service.AgentService agentService,
                          com.broksforge.modules.prompt.service.PromptService promptService,
                          KnowledgeGraphService knowledgeGraphService,
                          ModelAdvisor modelAdvisor,
                          CostAdvisor costAdvisor,
                          AgentAdvisor agentAdvisor,
                          RagAdvisor ragAdvisor,
                          PromptAdvisor promptAdvisor) {
        this.evaluationService = evaluationService;
        this.agentService = agentService;
        this.promptService = promptService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.modelAdvisor = modelAdvisor;
        this.costAdvisor = costAdvisor;
        this.agentAdvisor = agentAdvisor;
        this.ragAdvisor = ragAdvisor;
        this.promptAdvisor = promptAdvisor;
    }

    /** Project-wide advisory: model and cost optimisation across recent evaluation jobs. */
    @Transactional
    public AdvisoryReportResponse adviseProject(UUID actorId, UUID organizationId, UUID projectId) {
        List<EvaluationJobResponse> jobs = evaluationService.recentDetailed(actorId, organizationId, projectId);
        List<String> notes = new ArrayList<>();

        List<Recommendation> recs = new ArrayList<>();
        recs.addAll(modelAdvisor.analyze(jobs));
        recs.addAll(costAdvisor.analyze(jobs));

        if (jobs.isEmpty()) {
            notes.add("No evaluation jobs yet — run evaluations to unlock model and cost advice.");
        } else {
            notes.add("Analysed the %d most recent evaluation job(s) in this project.".formatted(jobs.size()));
        }
        return buildReport("PROJECT", "project", recs, notes);
    }

    /** Agent-scoped advisory: reliability, RAG configuration and model fit for one agent. */
    @Transactional
    public AdvisoryReportResponse adviseAgent(UUID actorId, UUID organizationId, UUID projectId, UUID agentId) {
        AgentResponse agent = agentService.get(actorId, organizationId, projectId, agentId);
        List<EvaluationJobResponse> agentJobs = evaluationService.recentDetailed(actorId, organizationId, projectId)
                .stream().filter(job -> agentId.equals(job.agentId())).toList();

        List<Recommendation> recs = new ArrayList<>();
        recs.addAll(agentAdvisor.analyze(agent, agentJobs));
        recs.addAll(ragAdvisor.analyze(agent));
        recs.addAll(modelAdvisor.analyze(agentJobs));

        List<String> notes = new ArrayList<>();
        if (agentJobs.isEmpty()) {
            notes.add("No recent evaluation jobs found for this agent; advice is based on its configuration only.");
        } else {
            notes.add("Analysed %d recent evaluation job(s) for this agent.".formatted(agentJobs.size()));
        }
        return buildReport("AGENT", agent.name(), recs, notes);
    }

    /** Prompt-scoped advisory: static analysis of a prompt version. */
    @Transactional
    public AdvisoryReportResponse advisePrompt(UUID actorId, UUID organizationId, UUID projectId,
                                               UUID promptId, UUID versionId) {
        PromptVersionResponse version = versionId != null
                ? promptService.getVersion(actorId, organizationId, projectId, promptId, versionId)
                : promptService.getVersionForExecution(actorId, organizationId, projectId, promptId, null);

        List<Recommendation> recs = new ArrayList<>(promptAdvisor.analyze(version));
        List<String> notes = new ArrayList<>();
        notes.add("Analysed prompt version v%d.".formatted(version.versionNumber()));
        if (recs.isEmpty()) {
            notes.add("No issues detected by the static prompt checks.");
        }
        return buildReport("PROMPT", "prompt v" + version.versionNumber(), recs, notes);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private AdvisoryReportResponse buildReport(String scope, String subject, List<Recommendation> recs,
                                               List<String> notes) {
        // Feed observed patterns back into the knowledge graph (learning seam).
        for (Recommendation rec : recs) {
            knowledgeGraphService.recordObservation(rec.knowledgeKey());
        }

        List<RecommendationResponse> ordered = recs.stream()
                .sorted(Comparator.comparingInt((Recommendation r) -> r.severity().ordinal()).reversed()
                        .thenComparing(r -> r.category().name()))
                .map(this::toResponse)
                .toList();

        Map<Severity, Long> counts = new EnumMap<>(Severity.class);
        for (Recommendation rec : recs) {
            counts.merge(rec.severity(), 1L, Long::sum);
        }
        List<SeverityCount> breakdown = new ArrayList<>();
        for (Severity severity : Severity.values()) {
            long count = counts.getOrDefault(severity, 0L);
            if (count > 0) {
                breakdown.add(new SeverityCount(severity, count));
            }
        }
        breakdown.sort(Comparator.comparingInt((SeverityCount sc) -> sc.severity().ordinal()).reversed());

        return new AdvisoryReportResponse(scope, subject, ordered.size(), breakdown, ordered, notes);
    }

    private RecommendationResponse toResponse(Recommendation rec) {
        return new RecommendationResponse(rec.category(), rec.title(), rec.why(), rec.whatChanged(), rec.howToFix(),
                rec.expectedImprovement(), rec.confidence(), rec.severity(), rec.evidence(), rec.knowledgeKey());
    }
}
