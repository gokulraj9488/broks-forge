package com.broksforge.modules.evaluation.domain;

/**
 * What an evaluation job executes against. {@code AGENT} (a registered agent's
 * endpoint) is the path implemented today; {@code MODEL} is reserved for future
 * provider-direct invocation through the same {@code ModelInvoker} SPI.
 */
public enum EvaluationTargetType {
    AGENT,
    MODEL
}
