package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.springframework.stereotype.Component;

/** Output equals the expected output (optionally case-insensitive, trimmed). */
@Component
public class ExactMatchMetric extends AbstractEvaluationMetric {

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.EXACT_MATCH;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        if (ctx.expectedOutput() == null) {
            return pass(spec, "No expected output to compare against");
        }
        boolean caseSensitive = boolParam(spec, "caseSensitive", false);
        String a = nullToEmpty(ctx.output()).trim();
        String b = ctx.expectedOutput().trim();
        boolean ok = caseSensitive ? a.equals(b) : a.equalsIgnoreCase(b);
        return outcome(spec, ok, null, ok ? "Output matches expected" : "Output differs from expected");
    }
}
