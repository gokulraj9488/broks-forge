package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.model.judge.JudgeInvocationService;
import org.springframework.stereotype.Component;

/**
 * A judge model scores the output 0.0-1.0 against a rubric (default: general
 * helpfulness/correctness relative to the input and, if present, the expected output) — the
 * other conversational default alongside {@link SemanticSimilarityMetric}.
 * Params: {@code providerId} (required), {@code model} (optional, provider default otherwise),
 * {@code rubric} (optional override), {@code context} (optional, falls back to expectedOutput).
 */
@Component
public class LlmJudgeMetric extends AbstractJudgeMetric {

    public LlmJudgeMetric(JudgeInvocationService judgeInvocationService) {
        super(judgeInvocationService);
    }

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.LLM_JUDGE;
    }

    @Override
    protected String defaultRubric() {
        return "You are an impartial evaluator judging an AI agent's response for overall quality. "
                + "Score how helpful, accurate and appropriate the MODEL OUTPUT is given the INPUT it was "
                + "responding to, using the CONTEXT / REFERENCE (if provided) as the ground truth for "
                + "factual correctness. 1.0 means an excellent, fully correct and helpful response; 0.0 "
                + "means unhelpful, incorrect, or off-topic.";
    }
}
