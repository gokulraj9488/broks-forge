package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.AbstractEvaluationMetric;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Output matches a regular expression ({@code params.pattern}). */
@Component
public class RegexMatchMetric extends AbstractEvaluationMetric {

    @Override
    public EvaluationMetricType type() {
        return EvaluationMetricType.REGEX_MATCH;
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        String pattern = strParam(spec, "pattern");
        if (pattern == null || pattern.isEmpty()) {
            return outcome(spec, false, null, "No regex pattern configured");
        }
        try {
            boolean fullMatch = boolParam(spec, "fullMatch", false);
            Pattern compiled = Pattern.compile(pattern);
            Matcher matcher = compiled.matcher(nullToEmpty(ctx.output()));
            boolean ok = fullMatch ? matcher.matches() : matcher.find();
            return outcome(spec, ok, null, ok ? "Output matches pattern" : "Output does not match pattern");
        } catch (PatternSyntaxException e) {
            return outcome(spec, false, null, "Invalid regex pattern");
        }
    }
}
