package com.broksforge.modules.evaluation.service.metric;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;
import com.broksforge.modules.evaluation.domain.MetricSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Scores a model output against a set of {@link MetricSpec}s. The engine itself is a thin,
 * pure dispatcher: it holds no per-metric logic and never needs a code change to add a metric
 * — each {@link EvaluationMetric} is a self-registering {@code @Component}, looked up here by
 * {@link EvaluationMetricType} (the same registry pattern as {@code ProviderAdapterRegistry}).
 *
 * <p>Dispatch is wrapped defensively: unlike the original pure/synchronous metrics, some
 * pluggable metrics (LLM Judge, Semantic Similarity) make a network call per row. A metric
 * throwing (timeout, provider error, bad config) must not abort the run or take down every
 * other metric on that row — it is caught and turned into a failed outcome with the exception
 * message as evidence.</p>
 */
@Service
public class EvaluationMetricEngine {

    private static final Logger log = LoggerFactory.getLogger(EvaluationMetricEngine.class);

    private final Map<EvaluationMetricType, EvaluationMetric> registry;

    public EvaluationMetricEngine(List<EvaluationMetric> metrics) {
        Map<EvaluationMetricType, EvaluationMetric> byType = new EnumMap<>(EvaluationMetricType.class);
        for (EvaluationMetric metric : metrics) {
            byType.put(metric.type(), metric);
        }
        this.registry = byType;
    }

    /**
     * Evaluates every spec against the context. When {@code specs} is empty a sensible
     * default rubric is used (non-empty output, plus exact-match when a reference exists).
     * This zero-config fallback is intentionally kept to the two purely local/synchronous
     * metrics — never a network-dependent one — so every pre-existing job with no metrics
     * configured keeps behaving exactly as before.
     */
    public List<MetricOutcome> evaluate(List<MetricSpec> specs, MetricContext context) {
        List<MetricSpec> effective = (specs == null || specs.isEmpty()) ? defaultSpecs(context) : specs;
        List<MetricOutcome> outcomes = new ArrayList<>(effective.size());
        for (MetricSpec spec : effective) {
            outcomes.add(evaluateOne(spec, context));
        }
        return outcomes;
    }

    private List<MetricSpec> defaultSpecs(MetricContext context) {
        List<MetricSpec> defaults = new ArrayList<>();
        defaults.add(new MetricSpec(EvaluationMetricType.NON_EMPTY, null, null, null, Map.of()));
        if (context.expectedOutput() != null && !context.expectedOutput().isBlank()) {
            defaults.add(new MetricSpec(EvaluationMetricType.EXACT_MATCH, null, null, null, Map.of()));
        }
        return defaults;
    }

    private MetricOutcome evaluateOne(MetricSpec spec, MetricContext ctx) {
        EvaluationMetric metric = registry.get(spec.type());
        if (metric == null) {
            return new MetricOutcome(spec.type(), labelOf(spec), null, null, spec.threshold(),
                    "No metric implementation registered for " + spec.type(), MetricExecutionStatus.INFRASTRUCTURE_ERROR);
        }
        try {
            return metric.evaluate(spec, ctx);
        } catch (Exception e) {
            log.warn("Metric {} failed to evaluate: {}", spec.type(), e.getMessage());
            String detail = "Metric evaluation failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return new MetricOutcome(spec.type(), labelOf(spec), null, null, spec.threshold(),
                    detail.length() <= 500 ? detail : detail.substring(0, 500), MetricExecutionStatus.INFRASTRUCTURE_ERROR);
        }
    }

    private String labelOf(MetricSpec spec) {
        return spec.label() != null ? spec.label() : spec.type().name();
    }
}
