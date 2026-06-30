# 11. AI Engineering Advisor and the on-read recommendation model

- Status: Accepted
- Date: 2026-07-01

## Context

Phase 3 made the platform able to **measure** agents — evaluations, benchmarks, regressions,
analytics. Phase 4's mission is to make it **advise**: not just display metrics, but answer
engineering questions and produce **actionable recommendations**. A dashboard that says "pass rate
is 72%" is data; an advisor that says "switch model X→Y for +8pp quality at equal cost, here is the
evidence" is engineering value.

We need a model for how recommendations are produced, shaped and stored that is consistent with the
rest of the platform (provider-agnostic, multi-tenant, modular monolith — see ./0001-modular-monolith.md),
that does not couple to one analysis, and that can grow new advisors without schema churn.

Two structural questions had to be answered:

1. **Are recommendations persisted, or computed on demand?**
2. **Where does the recommendation vocabulary live, and how do advisors stay decoupled?**

## Alternatives considered

- **Persist recommendations as a first-class aggregate.** A `recommendations` table written by a
  scheduled analyzer. Gives history and fast reads, but immediately drifts from the underlying data
  (a recommendation about a job whose model later changed becomes a lie), needs a scheduler and
  invalidation, and adds migrations for every new advisor. Heavy for findings that are cheap to
  recompute.
- **A conversational LLM "assistant".** Ask a model to critique the project. Flexible, but
  non-deterministic, expensive, hard to test, and explicitly *not* what was asked for — we want an
  engineering recommendation system, not a chatbot.
- **One monolithic analyzer.** A single service with all the heuristics. Simple at first, but
  becomes a god class and couples prompt logic to model logic to cost logic.

## Decision

Build the advisor as **on-read, composed, pure analyzers producing a fixed recommendation shape**:

1. **Recommendations are computed on read and never persisted.** Exactly like benchmark leaderboards
   and regression findings (Phase 3), an advisory report is derived from current platform data each
   time it is requested, so it can never drift. There are **no new tables** for the advisor.
2. **A fixed `Recommendation` value type** (a pure record in `modules/advisor/domain`) captures the
   questions an engineer asks: **why**, **what changed**, **how to fix**, **expected improvement**,
   **confidence**, **severity**, **evidence**, and an optional **knowledge key** linking to the
   Engineering Knowledge Graph (./0013-engineering-knowledge-graph.md). Every advisor and the
   root-cause engine speak this one vocabulary.
3. **Five pure sub-advisors**, one per concern — `PromptAdvisor`, `ModelAdvisor`, `CostAdvisor`,
   `AgentAdvisor`, `RagAdvisor` — each with no I/O (mirroring the pure `EvaluationMetricEngine`), so
   they are trivially unit-testable. `AdvisorService` loads data through other modules' **published
   services** and feeds the analyzers.
4. **Heuristics are configurable, not hard-coded.** Thresholds live in `AdvisorProperties`
   (`broksforge.advisor.*`) so teams calibrate sensitivity to their own baselines.
5. **The advisor reads, it does not mutate domain data.** The only write it performs is incrementing
   knowledge-graph occurrence counters (the learning seam) — never the agents, prompts or jobs it
   analyses.

## Consequences

**Positive**
- Advice is **always current** — derived from live state, never stale.
- **Zero schema cost** to ship the advisor and to add future advisors.
- Pure analyzers are **deterministic and unit-testable**; module boundaries stay clean (advisors
  compose published services, no cross-module JPA).
- The fixed `Recommendation` shape gives the UI a single thing to render and the knowledge graph a
  single thing to link to.

**Negative / trade-offs**
- **No recommendation history** out of the box: we cannot show "what we advised last week" without
  re-deriving from data that may have changed. Acceptable now; a periodic snapshot can be added later
  if history becomes a product requirement.
- Recompute-on-read costs CPU per request. Mitigated by bounding the analysis window (the most recent
  jobs) and by the analyzers being cheap, allocation-light passes.
- Heuristics can produce false positives. Mitigated by surfacing **confidence** on every finding and
  by making thresholds configurable.

## Future impact

- New advisors are **additive**: a new pure analyzer + wiring in `AdvisorService`, no migration.
- A future **LLM-as-judge** advisor can plug in behind the same `Recommendation` shape via the model
  SPI (./0006-provider-agnostic-model-invocation.md) without changing consumers.
- If recommendation **history** is needed, a snapshot table can be added without touching the
  analyzers — they remain the source of truth, the snapshot becomes an optional cache.
