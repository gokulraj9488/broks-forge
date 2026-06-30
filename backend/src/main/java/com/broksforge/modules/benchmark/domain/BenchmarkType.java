package com.broksforge.modules.benchmark.domain;

/**
 * The comparison dimension a benchmark expresses. The comparison itself is generic
 * (it ranks the entries' evaluation-job summaries); the type documents intent and
 * drives how the UI frames the leaderboard.
 */
public enum BenchmarkType {
    AGENT_VS_AGENT,
    VERSION_VS_VERSION,
    PROMPT_VS_PROMPT,
    MODEL_VS_MODEL,
    DATASET_VS_DATASET,
    PROFILE_VS_PROFILE
}
