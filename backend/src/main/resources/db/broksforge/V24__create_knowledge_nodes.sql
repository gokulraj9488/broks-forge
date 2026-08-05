-- ===========================================================================
-- V24: Engineering Knowledge Graph - nodes (Phase 4, ADR 0013)
-- ---------------------------------------------------------------------------
-- Platform-global reference data: a curated catalogue of failure modes,
-- regressions, recommendations and optimisations that the advisor and
-- root-cause engines link their findings to. Seeded with canonical patterns;
-- occurrence_count is the seam for future learning from real usage.
-- ===========================================================================

CREATE TABLE knowledge_nodes (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    created_by           UUID,
    updated_by           UUID,
    node_key             VARCHAR(80)  NOT NULL,
    node_type            VARCHAR(32)  NOT NULL,
    title                VARCHAR(200) NOT NULL,
    category             VARCHAR(48)  NOT NULL,
    summary              TEXT,
    detection_hint       TEXT,
    remediation          TEXT,
    expected_improvement VARCHAR(300),
    default_severity     VARCHAR(16)  NOT NULL,
    default_confidence   VARCHAR(16)  NOT NULL,
    tags                 TEXT,
    occurrence_count     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_knowledge_nodes PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_nodes_key UNIQUE (node_key)
);

CREATE INDEX idx_knowledge_nodes_type     ON knowledge_nodes (node_type);
CREATE INDEX idx_knowledge_nodes_category ON knowledge_nodes (category);

-- ---------------------------------------------------------------------------
-- Seed: canonical engineering patterns (deterministic UUIDs so edges can
-- reference them in V25). node_key values are stable identifiers the engines
-- link to in code.
-- ---------------------------------------------------------------------------
INSERT INTO knowledge_nodes
    (id, created_at, updated_at, node_key, node_type, title, category, summary,
     detection_hint, remediation, expected_improvement, default_severity, default_confidence, tags)
VALUES
    ('00000000-0000-4000-a000-000000000001', now(), now(), 'EMPTY_OUTPUT', 'FAILURE_MODE', 'Empty output', 'RELIABILITY',
     'The agent returned blank or whitespace-only output.',
     'Run output is null or blank, or the NON_EMPTY metric fails.',
     'Verify the endpoint contract and response field mapping, and ensure the prompt elicits a response.',
     'Converts blank responses into scored outputs and lifts pass rate.', 'HIGH', 'HIGH', '["reliability","output"]'),

    ('00000000-0000-4000-a000-000000000002', now(), now(), 'HTTP_ERROR', 'FAILURE_MODE', 'Endpoint HTTP error', 'RELIABILITY',
     'The agent endpoint returned a non-2xx HTTP status.',
     'Run HTTP status is 400 or greater, or the run failed during invocation.',
     'Inspect endpoint error responses and authentication; add retries for 5xx and fix 4xx request shape.',
     'Removes infrastructure failures that masquerade as quality failures.', 'HIGH', 'HIGH', '["reliability","http"]'),

    ('00000000-0000-4000-a000-000000000003', now(), now(), 'TIMEOUT', 'FAILURE_MODE', 'Invocation timeout', 'RELIABILITY',
     'The agent call exceeded the allotted time and was aborted.',
     'Run error mentions timeout or timed out.',
     'Add a bounded timeout and retries with backoff; profile the slow stage and consider a faster model.',
     'Eliminates transient timeout failures and recovers their pass rate.', 'HIGH', 'HIGH', '["reliability","latency"]'),

    ('00000000-0000-4000-a000-000000000004', now(), now(), 'JSON_PARSE_FAILURE', 'FAILURE_MODE', 'Output is not valid JSON', 'QUALITY',
     'The output could not be parsed as well-formed JSON.',
     'The JSON_VALID metric fails.',
     'Constrain the model to JSON via structured-output mode or a strict schema, and parse defensively.',
     'Reliably parseable output and fewer downstream failures.', 'HIGH', 'HIGH', '["quality","parsing"]'),

    ('00000000-0000-4000-a000-000000000005', now(), now(), 'EXACT_MATCH_MISS', 'FAILURE_MODE', 'Output does not match expected', 'QUALITY',
     'The output differs from the expected answer.',
     'EXACT_MATCH, CONTAINS or REGEX_MATCH metrics fail frequently.',
     'Tighten the prompt with an explicit task, format and example; verify dataset expectations and consider a higher-quality model.',
     'Higher pass rate on quality metrics.', 'MEDIUM', 'MEDIUM', '["quality","prompt"]'),

    ('00000000-0000-4000-a000-000000000006', now(), now(), 'HIGH_LATENCY', 'FAILURE_MODE', 'High latency', 'LATENCY',
     'Average run latency is above the configured threshold.',
     'Average latency exceeds the advisor latency guideline or the LATENCY metric fails.',
     'Profile the slowest stage, enable streaming, cache deterministic steps, or adopt a lower-latency model.',
     'Lower latency and higher throughput.', 'MEDIUM', 'MEDIUM', '["latency","performance"]'),

    ('00000000-0000-4000-a000-000000000007', now(), now(), 'COST_SPIKE', 'REGRESSION', 'Cost spike', 'COST',
     'Cost per job or per run increased beyond tolerance.',
     'Total cost regressed against a baseline, or the COST metric fails.',
     'Identify the cost driver (model change or token growth) and revert or optimise it.',
     'Returns spend toward the baseline.', 'HIGH', 'MEDIUM', '["cost","regression"]'),

    ('00000000-0000-4000-a000-000000000008', now(), now(), 'TOKEN_BLOAT', 'FAILURE_MODE', 'Token bloat', 'COST',
     'Token usage per run is higher than necessary.',
     'Average tokens per run is high, or the TOKEN_COUNT metric fails.',
     'Trim prompt context and cap output length; add a TOKEN_COUNT metric to catch regressions.',
     'Lower cost and latency proportional to the token reduction.', 'MEDIUM', 'MEDIUM', '["cost","tokens"]'),

    ('00000000-0000-4000-a000-000000000009', now(), now(), 'PROMPT_BLOAT', 'FAILURE_MODE', 'Prompt bloat', 'PROMPT',
     'The prompt template is large and wastes tokens.',
     'Template length exceeds the advisor guideline.',
     'Move static instructions into a system message, remove redundant context, and keep only changing variables.',
     'Lower per-call token cost and latency.', 'MEDIUM', 'HIGH', '["prompt","tokens"]'),

    ('00000000-0000-4000-a000-000000000010', now(), now(), 'PROMPT_CONTRADICTION', 'FAILURE_MODE', 'Contradicting instructions', 'PROMPT',
     'The prompt contains directives that conflict with each other.',
     'Opposing directive keywords co-occur in the template.',
     'Decide on the intended behaviour and remove the conflicting instruction.',
     'More consistent, predictable outputs.', 'MEDIUM', 'MEDIUM', '["prompt","quality"]'),

    ('00000000-0000-4000-a000-000000000011', now(), now(), 'PROMPT_INJECTION_RISK', 'FAILURE_MODE', 'Prompt injection exposure', 'PROMPT',
     'Untrusted input is interpolated without delimiters.',
     'The template interpolates variables with no delimiter around the value.',
     'Wrap interpolated values in explicit delimiters and instruct the model to treat them as data.',
     'Materially reduces prompt-injection exposure.', 'HIGH', 'MEDIUM', '["prompt","security"]'),

    ('00000000-0000-4000-a000-000000000012', now(), now(), 'RAG_LOW_SIMILARITY', 'FAILURE_MODE', 'Low retrieval similarity', 'RAG',
     'Retrieval admits weakly related chunks into context.',
     'Similarity threshold is low or retrieval configuration is absent.',
     'Raise the similarity threshold and return fewer, stronger passages.',
     'Better grounding and fewer hallucinated answers.', 'MEDIUM', 'MEDIUM', '["rag","retrieval"]'),

    ('00000000-0000-4000-a000-000000000013', now(), now(), 'RAG_CHUNK_OVERSIZED', 'FAILURE_MODE', 'Oversized retrieval chunks', 'RAG',
     'Retrieval chunk size is too large for precise grounding.',
     'Configured chunk size is very large.',
     'Reduce chunk size to roughly 300 to 800 tokens and re-index.',
     'Higher retrieval precision and fewer wasted tokens.', 'MEDIUM', 'MEDIUM', '["rag","chunking"]'),

    ('00000000-0000-4000-a000-000000000014', now(), now(), 'MODEL_OVERKILL', 'OPTIMIZATION', 'Model overkill', 'MODEL',
     'A more expensive model is used than the task requires.',
     'A cheaper model reaches comparable quality on the same workload.',
     'Shift traffic to the cheaper model where quality is equivalent.',
     'Lower cost at equivalent quality.', 'MEDIUM', 'MEDIUM', '["model","cost"]'),

    ('00000000-0000-4000-a000-000000000015', now(), now(), 'MISSING_RETRY', 'FAILURE_MODE', 'Missing retries', 'AGENT',
     'Transient failures are not retried, inflating the failure rate.',
     'A high share of runs fail to execute against a healthy model.',
     'Add bounded retries with backoff and a circuit breaker.',
     'Fewer transient failures; pass rate rises toward true quality.', 'MEDIUM', 'MEDIUM', '["agent","reliability"]'),

    ('00000000-0000-4000-a000-000000000016', now(), now(), 'MISSING_HEALTHCHECK', 'FAILURE_MODE', 'Missing health check', 'AGENT',
     'The agent has no recent successful health probe.',
     'Health status is UNKNOWN or no health check has run.',
     'Configure and run a health check and verify connectivity before trusting results.',
     'Distinguishes availability failures from quality failures.', 'MEDIUM', 'HIGH', '["agent","observability"]'),

    ('00000000-0000-4000-a000-000000000017', now(), now(), 'ADD_RETRIES', 'RECOMMENDATION', 'Add retries and timeouts', 'AGENT',
     'Wrap agent calls in bounded retries, backoff and timeouts.',
     'Recommended when timeouts or transient HTTP errors dominate.',
     'Implement retry with exponential backoff, a per-call timeout and a circuit breaker.',
     'Resilient execution and fewer transient failures.', 'MEDIUM', 'HIGH', '["agent","resilience"]'),

    ('00000000-0000-4000-a000-000000000018', now(), now(), 'TIGHTEN_PROMPT', 'RECOMMENDATION', 'Tighten the prompt', 'PROMPT',
     'Make the prompt explicit, delimited and free of contradictions.',
     'Recommended for prompt bloat, contradictions, injection risk or quality misses.',
     'State the task explicitly, delimit inputs, remove redundancy, and add one example.',
     'More consistent outputs and a higher pass rate.', 'MEDIUM', 'HIGH', '["prompt","quality"]'),

    ('00000000-0000-4000-a000-000000000019', now(), now(), 'SWITCH_CHEAPER_MODEL', 'RECOMMENDATION', 'Switch to a cheaper model', 'MODEL',
     'Use a lower-cost model that achieves equivalent quality.',
     'Recommended when a cheaper model matches quality at lower cost.',
     'Move the workload to the cheaper model and validate with an evaluation run.',
     'Lower cost at equivalent quality.', 'MEDIUM', 'MEDIUM', '["model","cost"]'),

    ('00000000-0000-4000-a000-000000000020', now(), now(), 'TUNE_RETRIEVAL', 'RECOMMENDATION', 'Tune retrieval', 'RAG',
     'Adjust chunking, overlap, top-k and similarity threshold.',
     'Recommended for low similarity or oversized chunks.',
     'Reduce chunk size, add overlap, lower top-k and raise the similarity threshold.',
     'Higher retrieval precision and grounding.', 'MEDIUM', 'MEDIUM', '["rag","retrieval"]');
