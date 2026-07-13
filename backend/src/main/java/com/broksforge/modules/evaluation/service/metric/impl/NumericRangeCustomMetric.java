package com.broksforge.modules.evaluation.service.metric.impl;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import com.broksforge.modules.evaluation.service.metric.CustomMetricEvaluator;
import com.broksforge.modules.evaluation.service.metric.MetricContext;
import com.broksforge.modules.evaluation.service.metric.MetricOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Example {@link CustomMetricEvaluator} proving out the {@code CUSTOM} extension point:
 * extracts the first numeric token from the output and checks it falls within
 * {@code [params.min, params.max]} — e.g. a satisfaction score an agent is instructed to emit.
 * Selected via {@code {"type": "CUSTOM", "params": {"key": "numeric-range", "min": 1, "max": 10}}}.
 */
@Component
public class NumericRangeCustomMetric implements CustomMetricEvaluator {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    @Override
    public String key() {
        return "numeric-range";
    }

    @Override
    public MetricOutcome evaluate(MetricSpec spec, MetricContext ctx) {
        String label = spec.label() != null ? spec.label() : spec.type().name();
        String output = ctx.output();
        Matcher matcher = NUMBER.matcher(output == null ? "" : output);
        if (!matcher.find()) {
            return new MetricOutcome(EvaluationMetricType.CUSTOM, label, false, BigDecimal.ZERO, spec.threshold(),
                    "No numeric value found in output");
        }
        BigDecimal value = new BigDecimal(matcher.group());
        BigDecimal min = decimalOf(spec.paramsOrEmpty().get("min"));
        BigDecimal max = decimalOf(spec.paramsOrEmpty().get("max"));
        boolean ok = (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
        String detail = "Value " + value.toPlainString() + " (min=" + (min == null ? "-" : min) + ", max="
                + (max == null ? "-" : max) + ")";
        return new MetricOutcome(EvaluationMetricType.CUSTOM, label, ok, ok ? BigDecimal.ONE : BigDecimal.ZERO,
                spec.threshold(), detail);
    }

    private BigDecimal decimalOf(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
