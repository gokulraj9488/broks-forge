# 12. Root-cause analysis engine

- Status: Accepted
- Date: 2026-07-01

## Context

A failed `EvaluationResult` is red, but red is not actionable. Phase 4 must explain **why** a job
failed or a regression occurred, in terms an engineer can act on: a root cause, the evidence behind
it, a confidence level, a recommended fix, the expected improvement, and a severity. This is the
difference between "23 runs failed" and "23 runs failed because the agent endpoint timed out — add
retries with backoff; expected to recover most of those runs."

The platform already stores the raw signal (run status, HTTP status, errors, per-metric pass/fail,
job summary, regression findings). The question is how to turn that signal into diagnoses without
coupling the diagnosis logic to evaluation internals, and without it becoming a god class.

## Alternatives considered

- **Inline the diagnosis in the evaluation module.** Closest to the data, but bleeds analysis
  concerns into the module that owns execution, and makes evaluation depend on the advisor vocabulary.
- **Rule engine / external DSL.** A configurable rules table or embedded rules engine. Powerful, but
  heavy and opaque for the dozen well-understood failure modes we actually have; harder to test than
  plain code.
- **LLM explanation.** Ask a model "why did this fail?". Non-deterministic, costly, and unverifiable
  — unacceptable for a diagnostic tool engineers must trust.

## Decision

A dedicated **`rootcause` module with a pure engine and a thin service**:

1. **`RootCauseEngine` is pure** (no I/O), mirroring the `EvaluationMetricEngine`. It takes already-
   loaded inputs — the job summary, the per-metric pass/fail tally, a bounded sample of failed runs,
   or a regression check's findings — and returns `RootCauseFinding`s. It is deterministic and
   directly unit-testable.
2. **`RootCauseFinding` matches the requested output exactly**: root cause, severity, confidence,
   evidence, recommendation, expected improvement, and an optional knowledge key.
3. **Data is loaded through published services**, not repositories of other modules. `RootCauseService`
   calls `EvaluationService` (new published reads: a single run, a bounded sample of failed runs, and
   a per-metric failure tally computed by a JPQL aggregate) and `RegressionService`. Tenant scoping is
   enforced once, inside the owning modules.
4. **Classification is by dominant failure mode.** Failed-run samples are bucketed (timeout, HTTP
   error, empty output) and per-metric tallies are mapped (JSON-invalid, exact-match miss, latency,
   cost, tokens), de-duplicated by knowledge key, and ranked by severity. Regression findings are
   diagnosed per regressed dimension.
5. **Findings link to the knowledge graph** (./0013-engineering-knowledge-graph.md) and feed its
   occurrence counters — the learning seam.

## Consequences

**Positive**
- Failures become **explainable and actionable**, not just visible.
- The engine is **pure and deterministic** — easy to test, no flakiness, no model cost.
- Module boundaries hold: `rootcause` depends on `advisor` (for the shared severity/confidence
  vocabulary), `evaluation`, `regression` and `knowledge` via published services only — an acyclic
  graph with no cross-module JPA.
- Findings are **on read**, so they always reflect current data (consistent with
  ./0011-ai-engineering-advisor.md).

**Negative / trade-offs**
- Heuristic classification can mis-rank when multiple failure modes mix. Mitigated by ranking by
  severity, capping the number of findings, and surfacing confidence and the supporting evidence.
- Analysis samples a **bounded** number of failed runs (configurable) rather than all of them, to
  keep reads cheap. The sample size is reported so the result is honest about its basis.
- Adding the published evaluation reads slightly widened the evaluation module's API. This is the
  correct trade — the module keeps ownership of its tables; consumers get neutral, scoped views.

## Future impact

- New failure modes are a **new branch in the engine plus a knowledge node** — no schema change.
- When live tracing lands (./0014-ai-debugger-and-tracing-seam.md), the engine can incorporate span
  data (which stage failed) for sharper diagnosis without changing its output shape.
- The same engine can power **CI gates** ("explain and block on this regression") through the
  existing regression check flow.
