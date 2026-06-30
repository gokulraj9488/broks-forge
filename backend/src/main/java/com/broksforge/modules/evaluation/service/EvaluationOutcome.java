package com.broksforge.modules.evaluation.service;

import java.util.Map;

/**
 * The aggregate result of executing an evaluation job: completed/failed counts and
 * the precomputed summary stored on the job.
 */
public record EvaluationOutcome(int completed, int failed, Map<String, Object> summary) {
}
