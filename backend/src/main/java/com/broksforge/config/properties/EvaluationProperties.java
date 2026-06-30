package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Evaluation execution configuration.
 *
 * @param maxItemsPerJob the largest dataset version a single synchronous job may run
 *                       (a guard rail until execution moves to async workers — ADR 0005)
 */
@Validated
@ConfigurationProperties(prefix = "broksforge.evaluation")
public record EvaluationProperties(int maxItemsPerJob) {
}
