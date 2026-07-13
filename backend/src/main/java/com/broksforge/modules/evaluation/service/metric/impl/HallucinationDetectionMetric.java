package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.model.judge.JudgeInvocationService;
import org.springframework.stereotype.Component;

/**
 * A judge model checks whether the output makes claims unsupported by the CONTEXT / REFERENCE
 * (params.context, falling back to the dataset's expected output, then the input alone).
 * Score is groundedness: 1.0 = every claim is supported, 0.0 = fabricated/unsupported claims
 * dominate. Params: {@code providerId} (required), {@code model} (optional), {@code context}
 * (optional), {@code rubric} (optional full override).
 */
@Component
public class HallucinationDetectionMetric extends AbstractJudgeMetric {

    public HallucinationDetectionMetric(JudgeInvocationService judgeInvocationService) {
        super(judgeInvocationService);
    }

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.HALLUCINATION_DETECTION;
    }

    @Override
    protected String defaultRubric() {
        return "You are checking an AI agent's response for hallucination. Compare every factual claim "
                + "in the MODEL OUTPUT against the CONTEXT / REFERENCE (if provided) or, if none is given, "
                + "against what can reasonably be inferred from the INPUT alone. Score how well-grounded the "
                + "output is: 1.0 means every claim is supported and nothing is fabricated; 0.0 means the "
                + "output invents facts, sources, or details not supported by the available context.";
    }
}
