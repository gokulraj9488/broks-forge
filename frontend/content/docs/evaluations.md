# Evaluations & Metrics

An evaluation is a **reproducible measurement**. It is the act that turns an opinion about your AI
system into evidence.

## Why reproducibility is the whole point

An evaluation pins its configuration when it is created: the agent, the prompt revision, the dataset
version, the provider and the model. Reading the result six months later still tells you exactly
what was measured.

This is what lets an evaluation become [Evidence](/docs/core-concepts). A number produced by an
unknown configuration cannot support a decision, because nobody can say what it was a number *about*.

## Anatomy

```
   EVALUATION JOB                     status: PENDING → RUNNING → COMPLETED | FAILED | CANCELLED
   ├── pinned agent
   ├── pinned dataset version
   ├── pinned prompt revision
   ├── provider + model
   └── RUNS  (one per dataset item)
        ├── input, output
        ├── latency, prompt/completion/total tokens, cost
        ├── HTTP status
        ├── metric results  → passed / score
        └── error, if any
```

A **run** is the unit of truth. Everything above it — pass rates, summaries, verdicts — is computed
from real runs, and everything below it — the [Execution Graph](/docs/execution-graph), the failure
classifier, the investigation — reads them directly.

## The metric catalog

Fourteen built-in metric types, in three categories.

### Quality

| Metric | Checks |
| --- | --- |
| `EXACT_MATCH` | Output equals the expected answer |
| `CONTAINS` | Output contains an expected substring |
| `REGEX_MATCH` | Output matches a pattern |
| `JSON_VALID` | Output parses as valid JSON |
| `NON_EMPTY` | Output is not blank |
| `LENGTH` | Output length falls within bounds |
| `SEMANTIC_SIMILARITY` | Embedding similarity to the expected answer |
| `LLM_JUDGE` | A model scores the response against a rubric |
| `HALLUCINATION_DETECTION` | Claims unsupported by the provided context |
| `CITATION_VERIFICATION` | Citations match the supplied sources |
| `CUSTOM` | Your own evaluator |

### Performance

| Metric | Checks |
| --- | --- |
| `LATENCY` | Response time against a threshold |

### Cost

| Metric | Checks |
| --- | --- |
| `COST` | Per-run spend against a budget |
| `TOKEN_COUNT` | Token usage against a ceiling |

**Quality is never reported without its price.** Wherever the platform states a quality result, it
states the latency and cost alongside it. A prompt that is three points better and four times more
expensive is not straightforwardly better, and the interface refuses to imply that it is.

## The failure classifier

When runs fail, Broks Forge does not lump every non-2xx into "the endpoint returned errors". It
classifies them, because each class has a different fix:

| Class | Signal | What it means |
| --- | --- | --- |
| **Timeout** | Timeout in the error text | The call is too slow — profile or change model |
| **Network** | DNS, connection, TLS, policy block | The request never reached the provider |
| **Authentication** | HTTP 401 / 403 | Credentials rejected |
| **Quota** | HTTP 402, or 429 reporting credit/billing | The account is out of credit |
| **Rate limit** | HTTP 429 | Too many requests — reduce concurrency |
| **Invalid model** | HTTP 404 naming a model | The model id does not exist for this provider |
| **Infrastructure** | HTTP ≥ 500 | Provider-side; retry, do not rewrite the prompt |
| **HTTP error** | Other 4xx | Malformed request shape |
| **Empty output** | Blank output, no error | Contract or parsing problem |
| **Unclassified** | No known pattern | Surfaced for manual triage, never hidden |

Metric *execution* failures are tracked separately from metric *results*. A judge call that was rate
limited never produced a score — reporting that as "the model scored badly" would be a lie, so a
metric tallies as either "ran and scored low" or "never ran", never both.

This classifier is what supplies the **immediate cause** in the
[Root Cause Explorer](/docs/root-cause-explorer).

## Regression checks and benchmarks

**Benchmarks** compare variants — several agents, prompts or models over the same dataset — so a
choice between them rests on the same measurement.

**Regression checks** compare a candidate against a baseline across quality, latency, cost and
tokens, and report which dimensions regressed and by how much.

## After an evaluation completes

This is where Broks Forge diverges from an evaluation tool. The result does not just appear on a
dashboard — it enters the engineering record:

- an **Observation** is derived
- if it covers a promoted revision, it becomes **Evidence**
- combined with a decision, it can produce **Knowledge**
- it appears in the **Forge Graph**, connected to everything it measured
- it becomes searchable **precedent** for future failures

See also: [Execution Graph](/docs/execution-graph) · [Core Concepts](/docs/core-concepts) ·
[Root Cause Explorer](/docs/root-cause-explorer)
