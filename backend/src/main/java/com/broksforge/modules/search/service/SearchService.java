package com.broksforge.modules.search.service;

import com.broksforge.modules.agent.service.AgentService;
import com.broksforge.modules.agent.web.dto.AgentFilter;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.dataset.web.dto.DatasetFilter;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobFilter;
import com.broksforge.modules.prompt.service.PromptService;
import com.broksforge.modules.prompt.web.dto.PromptFilter;
import com.broksforge.modules.search.web.dto.SearchDtos.SearchHit;
import com.broksforge.modules.search.web.dto.SearchDtos.SearchResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Global search across the project's agents, datasets, prompts and evaluation jobs.
 * It composes each module's published search service (which enforce membership and
 * project scoping), so results never cross tenant or project boundaries.
 */
@Service
public class SearchService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 20;

    private final AgentService agentService;
    private final DatasetService datasetService;
    private final PromptService promptService;
    private final EvaluationService evaluationService;

    public SearchService(AgentService agentService,
                         DatasetService datasetService,
                         PromptService promptService,
                         EvaluationService evaluationService) {
        this.agentService = agentService;
        this.datasetService = datasetService;
        this.promptService = promptService;
        this.evaluationService = evaluationService;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(UUID actorId, UUID organizationId, UUID projectId, String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return new SearchResponse(query, List.of());
        }
        int perType = Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit));
        PageRequest page = PageRequest.of(0, perType);
        List<SearchHit> hits = new ArrayList<>();

        agentService.search(actorId, organizationId, projectId,
                        new AgentFilter(query, null, null, null, null, null, null), page)
                .content()
                .forEach(a -> hits.add(new SearchHit("AGENT", a.id(), a.name(), a.slug())));

        datasetService.search(actorId, organizationId, projectId,
                        new DatasetFilter(query, null, null, null), page)
                .content()
                .forEach(d -> hits.add(new SearchHit("DATASET", d.id(), d.name(), d.slug())));

        promptService.search(actorId, organizationId, projectId,
                        new PromptFilter(query, null, null), page)
                .content()
                .forEach(p -> hits.add(new SearchHit("PROMPT", p.id(), p.name(), p.slug())));

        evaluationService.search(actorId, organizationId, projectId,
                        new EvaluationJobFilter(query, null, null, null), page)
                .content()
                .forEach(j -> hits.add(new SearchHit("EVALUATION_JOB", j.id(), j.name(), j.status().name())));

        return new SearchResponse(query, hits);
    }
}
