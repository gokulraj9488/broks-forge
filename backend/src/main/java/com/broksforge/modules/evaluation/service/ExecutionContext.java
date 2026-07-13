package com.broksforge.modules.evaluation.service;

import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.model.ModelTarget;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The fully-resolved, non-item inputs for executing a job: target, template, metric rubric and
 * the dataset reference to page through. Split out from {@link EvaluationPlan} so the background
 * runner can resolve everything once per job and then stream dataset rows page by page, instead
 * of holding the whole dataset (which may be millions of rows) in memory as {@link EvaluationPlan}
 * requires for the synchronous path.
 */
public record ExecutionContext(
        UUID jobId,
        UUID organizationId,
        UUID projectId,
        LlmProvider provider,
        String model,
        Map<String, Object> parameters,
        ModelTarget target,
        String template,
        List<MetricSpec> metrics,
        BigDecimal passThreshold,
        UUID datasetId,
        UUID datasetVersionId
) {
    /** An {@link EvaluationPlan} for a single page of rows (items unused by row-level execution). */
    public EvaluationPlan toPlan(List<com.broksforge.modules.dataset.service.DatasetRow> items) {
        return new EvaluationPlan(jobId, organizationId, projectId, provider, model, parameters, target, template,
                metrics, passThreshold, items);
    }
}
