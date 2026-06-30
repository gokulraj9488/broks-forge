package com.broksforge.modules.evaluation.service;

import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.dataset.service.DatasetRow;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.model.ModelTarget;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The fully-resolved, immutable inputs for executing one evaluation job: the target,
 * the (optional) prompt template, the metric rubric, and the pinned dataset rows.
 * Resolving everything up front keeps the executor free of access checks and lets
 * the network calls run outside any transaction.
 */
public record EvaluationPlan(
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
        List<DatasetRow> items
) {
}
