package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.springframework.stereotype.Component;

/** Output contains a substring ({@code params.value}, or the expected output). */
@Component
public class ContainsMetric extends AbstractEvaluationMetric {

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.CONTAINS;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        String needle = strParam(spec, "value");
        if (needle == null) {
            needle = ctx.expectedOutput();
        }
        if (needle == null || needle.isEmpty()) {
            return pass(spec, "No substring to search for");
        }
        boolean caseSensitive = boolParam(spec, "caseSensitive", false);
        String haystack = nullToEmpty(ctx.output());
        boolean ok = caseSensitive
                ? haystack.contains(needle)
                : haystack.toLowerCase().contains(needle.toLowerCase());
        return outcome(spec, ok, null, ok ? "Output contains expected substring" : "Substring not found");
    }
}
