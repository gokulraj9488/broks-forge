package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.model.judge.JudgeInvocationService;
import org.springframework.stereotype.Component;

/**
 * A judge model checks that citations/sources referenced in the output are consistent with the
 * CONTEXT / REFERENCE (params.context, falling back to the expected output). Score 1.0 = every
 * citation is accurate and traceable to the context; 0.0 = citations are invented, mismatched,
 * or missing entirely where the response implies a source. Params: {@code providerId}
 * (required), {@code model} (optional), {@code context} (optional), {@code rubric} (optional).
 */
@Component
public class CitationVerificationMetric extends AbstractJudgeMetric {

    public CitationVerificationMetric(JudgeInvocationService judgeInvocationService) {
        super(judgeInvocationService);
    }

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.CITATION_VERIFICATION;
    }

    @Override
    protected String defaultRubric() {
        return "You are verifying citations in an AI agent's response. Check every citation, reference, "
                + "or attributed source in the MODEL OUTPUT against the CONTEXT / REFERENCE (if provided). "
                + "Score 1.0 if every citation is accurate, properly attributed, and traceable to the given "
                + "context (or no citation was needed/made); score 0.0 if the output invents a source, "
                + "misattributes a claim, or cites something absent from the context.";
    }
}
