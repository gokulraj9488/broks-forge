package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import com.broksforge.modules.model.judge.JudgeInvocationService;
import com.broksforge.modules.model.judge.JudgeVerdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Shared plumbing for the three judge-model-backed metrics (LLM Judge, Hallucination Detection,
 * Citation Verification): each supplies its own rubric text, this base class resolves
 * {@code providerId}/{@code model} params, assembles the judge prompt, calls
 * {@link JudgeInvocationService}, and turns the verdict into a {@link MetricOutcome}.
 */
abstract class AbstractJudgeMetric extends AbstractEvaluationMetric {

    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.7");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JudgeInvocationService judgeInvocationService;

    protected AbstractJudgeMetric(JudgeInvocationService judgeInvocationService) {
        this.judgeInvocationService = judgeInvocationService;
    }

    /** The fixed rubric instructions for this metric (may reference {contextLabel} conventions in subclasses). */
    protected abstract String defaultRubric();

    @Override
    public final MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        UUID providerId = uuidParam(spec, "providerId");
        String model = strParam(spec, "model");
        String rubric = strParam(spec, "rubric");
        if (rubric == null || rubric.isBlank()) {
            rubric = defaultRubric();
        }
        String context = resolveContext(spec, ctx);

        String prompt = rubric
                + "\n\nRespond with strict JSON only, no markdown fences: {\"score\": <0.0-1.0>, \"reasoning\": "
                + "\"<one sentence>\", \"criteria\": {\"<CriterionName>\": <0-10 integer>, ...}}. Include 2-4 named "
                + "criteria relevant to this judgment (e.g. Correctness, Helpfulness, Professionalism) scored 0-10 "
                + "each; omit \"criteria\" entirely if the judgment doesn't naturally break into sub-criteria."
                + "\n\n--- INPUT ---\n" + nullToEmpty(ctx.input())
                + "\n\n--- CONTEXT / REFERENCE ---\n" + (context == null || context.isBlank() ? "(none provided)" : context)
                + "\n\n--- MODEL OUTPUT TO JUDGE ---\n" + nullToEmpty(ctx.output());

        JudgeVerdict verdict = judgeInvocationService.judge(providerId, model, prompt);
        if (!verdict.ok()) {
            MetricExecutionStatus status = MetricExecutionStatus.classify(verdict.httpStatus(), verdict.error());
            return executionError(spec, status, verdict.error());
        }
        BigDecimal threshold = spec.threshold() != null ? spec.threshold() : DEFAULT_THRESHOLD;
        boolean passed = verdict.score().compareTo(threshold) >= 0;
        String detail = buildDetailJson(verdict);
        return scoredOutcome(spec, passed, verdict.score(), threshold, detail);
    }

    /**
     * Writes a compact JSON envelope (score/reasoning/criteria) instead of a human sentence, so
     * the Runs UI can render a structured, expandable per-criterion breakdown. Falls back to a
     * plain sentence if serialization somehow fails — never lets a display-formatting problem
     * fail the metric itself.
     */
    private String buildDetailJson(JudgeVerdict verdict) {
        try {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("score", verdict.score());
            if (verdict.reasoning() != null) {
                node.put("reasoning", verdict.reasoning());
            }
            if (verdict.criteria() != null && !verdict.criteria().isEmpty()) {
                node.set("criteria", OBJECT_MAPPER.valueToTree(verdict.criteria()));
            }
            return truncate(OBJECT_MAPPER.writeValueAsString(node));
        } catch (Exception e) {
            return truncate("Judge score " + verdict.score()
                    + (verdict.reasoning() != null ? " — " + verdict.reasoning() : ""));
        }
    }

    /** {@code params.context}, falling back to the dataset's expected output, then null (input-only judging). */
    private String resolveContext(MetricSpec spec, MetricContext ctx) {
        String explicit = strParam(spec, "context");
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        return ctx.expectedOutput();
    }
}
