package com.broksforge.modules.model.judge;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The result of a judge-model call: either a parsed {@code score} (0.0-1.0) with the judge's
 * own reasoning and, optionally, a per-criterion breakdown (e.g. {@code {"Correctness": 9,
 * "Helpfulness": 10}} on a 0-10 scale — distinct from the overall 0.0-1.0 {@code score} used for
 * pass/fail threshold comparison), or an {@code error} when the provider/config is unusable or
 * the judge's response couldn't be parsed. Metrics built on {@link JudgeInvocationService} turn
 * an error into a failed {@code MetricOutcome} rather than letting the exception propagate and
 * take down the rest of the run's metrics.
 *
 * <p>{@code httpStatus} is the raw status the judge provider returned, when there was one (null
 * for a failure that never got an HTTP response — timeout, connection refused, network-policy
 * block, or a config/parse problem). Callers classify it via
 * {@code MetricExecutionStatus.classify(httpStatus, error)} to distinguish a transport/auth
 * failure from an actual low judge score.</p>
 */
public record JudgeVerdict(boolean ok, BigDecimal score, String reasoning, Map<String, Object> criteria,
                            String error, Integer httpStatus) {

    public static JudgeVerdict of(BigDecimal score, String reasoning) {
        return new JudgeVerdict(true, score, reasoning, Map.of(), null, null);
    }

    public static JudgeVerdict of(BigDecimal score, String reasoning, Map<String, Object> criteria) {
        return new JudgeVerdict(true, score, reasoning, criteria == null ? Map.of() : criteria, null, null);
    }

    public static JudgeVerdict error(String message) {
        return new JudgeVerdict(false, null, null, Map.of(), message, null);
    }

    public static JudgeVerdict error(String message, Integer httpStatus) {
        return new JudgeVerdict(false, null, null, Map.of(), message, httpStatus);
    }
}
