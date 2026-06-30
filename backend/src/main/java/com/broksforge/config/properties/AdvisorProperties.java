package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tuning thresholds for the AI Engineering Advisor (Phase 4). These are heuristics,
 * not hard limits: they shape when an advisor raises a recommendation, and are
 * configurable so teams can calibrate sensitivity to their own baselines.
 *
 * @param promptMaxChars          a prompt template longer than this is flagged as bloated
 * @param promptMaxVariables      a template declaring more variables than this is flagged as complex
 * @param latencySpikeMs          an average run latency above this (ms) is flagged as a latency concern
 * @param minSamplesForComparison the minimum number of comparable jobs before model/cost comparisons are emitted
 * @param failureSampleSize       the maximum number of failed runs the root-cause engine samples per job
 */
@Validated
@ConfigurationProperties(prefix = "broksforge.advisor")
public record AdvisorProperties(
        int promptMaxChars,
        int promptMaxVariables,
        long latencySpikeMs,
        int minSamplesForComparison,
        int failureSampleSize
) {
}
