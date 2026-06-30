# 5. Evaluation Job as the top-level aggregate

- Status: Accepted
- Date: 2026-07-01

## Context

Evaluation is the first large feature area to attach to an agent (see
./0002-agent-as-central-entity.md). It must scale to **millions of executions**: a single
evaluation request runs an agent version against every item in a dataset and scores each
output against multiple metrics. The cardinality is therefore `items × metrics` per request,
and the platform will run many requests concurrently across many agents.

An individual "evaluation" — one agent output scored by one metric on one dataset item — is the
natural unit of *measurement*, but it is far too fine-grained to be the top-level object users
create, track, paginate, cancel or report on. We need an aggregate that owns the lifecycle and
orchestration of a whole evaluation, while the fine-grained measurements remain queryable and
independently attributable.

## Alternatives considered

- **Flat "Evaluation" rows.** One table where each row is a single item×metric measurement and
  the "job" is implied by a shared correlation id. Simple to write, but there is no first-class
  object to hold status, progress, a summary or a cancel signal; every list/aggregate query
  becomes a `GROUP BY` over millions of rows.
- **Per-prompt (or per-item) jobs.** Make the unit of orchestration a single prompt run. This
  pushes fan-out and aggregation up into the caller, loses a single place to track overall
  progress, and makes partial-failure reporting and pagination the client's problem.
- **Embedding results as JSON on the job.** Keep one job row with a large JSON blob of all runs
  and results. Avoids extra tables but is unqueryable, unbounded in row size, impossible to
  paginate, and forfeits per-result attribution to an `agentVersionId`.

## Decision

Make **`EvaluationJob` the aggregate root and orchestration entry point** for evaluation, with a
three-level structure beneath it:

1. **`EvaluationJob`** — the top-level object a user creates and tracks. It pins the inputs
   (`agentVersionId`, dataset version, metric set), owns the **status lifecycle**
   `PENDING → RUNNING → COMPLETED / FAILED / CANCELLED`, and carries a **precomputed summary**
   (counts, pass/fail, aggregate scores) so dashboards and lists never re-aggregate the leaf
   rows.
2. **`EvaluationRun`** — one row **per dataset item**, recording the agent's output and per-item
   outcome. This is the fan-out level and the unit of partial-failure isolation: one item's
   failure marks that run failed without aborting the job.
3. **`EvaluationResult`** — one row **per metric** within a run, holding the score and verdict.
   Every result carries the `agentVersionId` so measurements are attributable to the exact agent
   version that produced them, supporting later benchmarking and regression.

Execution flows through an **`EvaluationJobExecutor`**, the single seam that drives a job from
`PENDING` to a terminal state. It is **synchronous today** (in-process, one job at a time) but is
deliberately shaped as the **async/queue-ready boundary**: nothing outside the executor assumes
synchronous completion, so a queue/worker can later be slotted in without touching the domain
model or the API.

## Consequences

**Positive**
- Clean scaling and pagination: lists and dashboards read the job and its summary, never the
  millions of leaf rows.
- Partial-failure isolation: a single bad item fails its `EvaluationRun`, not the whole job.
- Precise attribution: every `EvaluationResult` ties a metric score to an `agentVersionId`,
  which is exactly what benchmarking and regression need.
- A single, well-defined async seam (`EvaluationJobExecutor`) instead of a synchronous assumption
  baked across the codebase.

**Negative / trade-offs**
- More tables and more joins than a flat design.
- **Write amplification**: one row per run *and* one per metric means a single job can emit a
  very large number of rows. We accept this as the cost of queryability and attribution, and
  mitigate it with the precomputed job summary and (later) partitioning/retention.
- The summary is denormalised state that must be kept consistent with the leaf rows by the
  executor.

## Future impact

- The synchronous `EvaluationJobExecutor` is replaced by async workers consuming a queue, with no
  change to the aggregate or API contract.
- High-cardinality `EvaluationRun` / `EvaluationResult` tables become candidates for
  **partitioning and retention policies** without affecting the job-level interface.
- Benchmarking and regression detection are built **on top of job summaries** and the
  per-`agentVersionId` results, comparing jobs rather than re-running raw measurements.
