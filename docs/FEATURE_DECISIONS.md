# Feature Decisions — Brok's Forge

> A running log of notable **product and engineering decisions** for Brok's Forge.

This log captures decisions that shape the product's behavior and the engineer's mental model —
the "we chose X over Y, and here's why." It complements the [ADRs](./adr/README.md): ADRs record
deep architectural/security decisions in detail, while this log is the chronological index that
links day-to-day feature decisions back to those ADRs and to the [project rules](./PROJECT_RULES.md).

Each entry records **Context**, **Decision**, **Status**, and a **link to the relevant ADR**.
Entries are append-only and never rewritten; a reversed decision gets a new, later entry.

---

## FD-001 — `Agent` is the central platform entity

- **Date:** 2026-07-01
- **Context:** Brok's Forge is "the engineering platform for AI agents." Every planned module —
  evaluation, benchmark, regression, prompt, dataset, model, tracing, analytics, the SDK and CLI —
  ultimately operates *on an agent*. Without one stable concept to attach to, each module would
  invent its own notion of "the thing under test" and the platform would fragment. Agents are also
  framework-diverse (Spring AI, LangGraph, CrewAI, custom REST, …), so the model cannot bind to any
  one framework's types.
- **Decision:** Make `Agent` the **central aggregate root**, modeled framework-neutrally
  (metadata + open-ended `customMetadata`) with a stable **UUID identity**. Every other module
  attaches to an agent — and where relevant an `agentVersionId` — **by id**, never by a JPA
  association. `AgentVersion` carries deployment history (model, provider, framework version, git
  SHA, prompt version, environment) so results can be attributed to an exact version.
- **Status:** Accepted.
- **ADR:** [ADR 0002 — Agent as the central platform entity](./adr/0002-agent-as-central-entity.md).

---

## FD-002 — `EvaluationJob` (not `Evaluation`) is the top-level evaluation object

- **Date:** 2026-07-01
- **Context:** Evaluation is the core of the Intelligence Layer and the part of the system that must
  **scale to millions** of records. A flat `Evaluation` entity would conflate three distinct
  concerns — what was requested, each execution attempt, and each scored data point — and would not
  give us a natural unit of work to queue, retry, or parallelize later.
- **Decision:** Model evaluation as a **hierarchy with `EvaluationJob` at the top**:
  `EvaluationJob → EvaluationRun → EvaluationResult`, plus a summary roll-up.
  - `EvaluationJob` is the requested unit of work (target agent/version, dataset version, prompt
    version, model, `EvaluationProfile`) and the object with a lifecycle **status**.
  - `EvaluationRun` is one execution of that job (enabling retries and re-runs).
  - `EvaluationResult` is one scored data point per dataset item, scored by the
    `EvaluationMetricType` engine — this is the table that grows to millions of rows.
  - A **summary** aggregates results for fast reads (leaderboards, dashboards) without scanning the
    result table.
  Naming it `EvaluationJob` makes the queueable, schedulable, retryable nature explicit and gives
  P6 a clean unit to push onto async workers.
- **Status:** Accepted.
- **ADR:** [ADR 0002 — Agent as the central platform entity](./adr/0002-agent-as-central-entity.md)
  (results attach to `agentVersionId`); scale rationale recorded under
  [PROJECT_RULES.md → ARCH-6](./PROJECT_RULES.md).

---

## FD-003 — Datasets are immutable, versioned artifacts

- **Date:** 2026-07-01
- **Context:** An evaluation is only trustworthy if it can be reproduced. If a dataset can be edited
  in place, an evaluation result loses meaning the moment its underlying data changes — two runs
  "against the same dataset" may not be comparable, and historical results become unverifiable.
- **Decision:** Model the dataset as `Dataset` → `DatasetVersion` → `DatasetItem`, where a
  **`DatasetVersion` is immutable** once created. Imports (CSV + JSON) produce a new version;
  edits never mutate an existing version. Per-version **statistics** are computed and stored.
  Evaluations and benchmarks reference an exact `DatasetVersion`, guaranteeing reproducibility.
- **Status:** Accepted.
- **ADR:** [ADR 0001 — Modular monolith over microservices](./adr/0001-modular-monolith.md)
  (immutable, id-referenced artifacts reinforce clean module boundaries).

---

## FD-004 — Prompts use `{{variable}}` templating with versioning and rollback

- **Date:** 2026-07-01
- **Context:** Prompts are the most frequently iterated asset in agent engineering and a primary
  driver of quality. Engineers need to parameterize a prompt across dataset items, compare two
  wordings objectively, and revert a bad change quickly — and every evaluation must pin the exact
  prompt text it used.
- **Decision:** Model prompts as `Prompt` → `PromptVersion`. Templates use **`{{variable}}`**
  placeholders rendered per dataset item. Every change creates a new immutable `PromptVersion`,
  enabling **compare** between versions and one-click **rollback** to a previous version.
  Evaluation/benchmark/regression reference a specific `promptVersionId`, so results are always tied
  to exact prompt text (the `AgentVersion.promptVersion` field anticipated this link).
- **Status:** Accepted.
- **ADR:** [ADR 0002 — Agent as the central platform entity](./adr/0002-agent-as-central-entity.md)
  (`AgentVersion.promptVersion` was the forward reference to this module).

---

## FD-005 — Provider-agnostic model SPI, with `AgentEndpointInvoker` as the concrete target

- **Date:** 2026-07-01
- **Context:** Evaluation, benchmarking, and regression must call models and agents across many
  vendors (OpenAI, Anthropic, Groq, Ollama, Gemini, OpenRouter, DeepSeek). Coupling the engine to
  any single vendor SDK would make the rest of the platform vendor-locked and make adding a provider
  a cross-cutting change.
- **Decision:** Introduce a **provider-agnostic `ModelInvoker` SPI**. The primary concrete target
  for the platform's own purpose — invoking a registered agent — is **`AgentEndpointInvoker`**,
  which calls the agent's `endpointUrl` (resolving encrypted credentials and passing through
  `OutboundUrlGuard`). Direct-provider invokers implement the same SPI. Adding a provider is a
  **code-only** change behind the interface, with no schema or domain impact.
- **Status:** Accepted.
- **ADR:** [ADR 0004 — SSRF protection for outbound agent calls](./adr/0004-ssrf-protection-for-agent-endpoints.md)
  (every invoker that reaches an agent endpoint goes through the outbound guard).

---

## FD-006 — Encrypt agent credentials (not hash)

- **Date:** 2026-07-01
- **Context:** The platform must **replay** an agent's credential to the agent's endpoint at call
  time (health checks now; invocation, evaluation, benchmarking next). A one-way hash — correct for
  passwords and API key secrets we only need to *verify* — is unusable here, because you cannot send
  a hash as the upstream credential.
- **Decision:** Keep **hashing** for verification secrets (BCrypt passwords, SHA-256 tokens), but
  **encrypt** agent (usage) credentials with **AES-256-GCM** via `CredentialEncryptionService`:
  random 96-bit IV per value, 128-bit GCM auth tag, 256-bit key supplied only via
  `BROKSFORGE_SECURITY_ENCRYPTION_KEY`, and version-stamped ciphertext for key rotation. Credentials
  are **write-only** over the API; responses return masked metadata only; decryption happens solely
  for internal outbound calls and the secret is never logged or returned.
- **Status:** Accepted.
- **ADR:** [ADR 0003 — Encrypt agent credentials instead of hashing](./adr/0003-credential-encryption-vs-hashing.md).

---

## FD-007 — Reports are on-demand exports (JSON / CSV / HTML); PDF deferred

- **Date:** 2026-07-01
- **Context:** Users need to extract evaluation, benchmark, and analytics data to share and archive.
  A full server-side PDF rendering pipeline (layout engine, fonts, headless rendering) is heavy and
  would slow down delivery of the core Intelligence Layer, while most immediate needs are met by
  machine- and human-readable formats.
- **Decision:** Ship reports as **on-demand exports** in **JSON, CSV, and HTML**, generated when
  requested rather than precomputed. Structure the report model to be **PDF-ready** (HTML designed
  for clean print/PDF conversion) so PDF can be added later without reworking the data model. **PDF
  rendering is deferred** to a future feature.
- **Status:** Accepted.
- **ADR:** [ADR 0001 — Modular monolith over microservices](./adr/0001-modular-monolith.md)
  (report is a thin module consuming other modules' data by id, adding no new coupling).

---

## FD-008 — Synchronous `EvaluationJobExecutor` now, with a queue-ready seam

- **Date:** 2026-07-01
- **Context:** The evaluation pipeline must eventually run asynchronously on horizontally scalable
  workers (P6) to sustain millions of results. Building that distributed infrastructure now would
  delay the Intelligence Layer and add operational complexity before there is volume to justify it.
- **Decision:** Implement evaluation execution **synchronously today** via an
  `EvaluationJobExecutor`, but design the boundary as a **queue-ready seam**: the executor consumes
  an `EvaluationJob` as a self-contained unit of work, status transitions are explicit, and the
  domain holds no assumptions about running on the request thread. Swapping in durable queues and
  async workers in P6 becomes an infrastructure change behind that seam, not a domain rewrite.
- **Status:** Accepted.
- **ADR:** [ADR 0001 — Modular monolith over microservices](./adr/0001-modular-monolith.md)
  (the seam is the same extraction seam that lets a module later become its own service);
  see also [PROJECT_RULES.md → ARCH-6](./PROJECT_RULES.md).

---

## FD-009 — The advisor is a recommendation engine, computed on read (not a chatbot, not persisted)

- **Date:** 2026-07-01
- **Context:** Phase 4 must turn measurement into advice — answer engineering questions and emit
  actionable recommendations. Two failure modes to avoid: a non-deterministic conversational
  "assistant" (untestable, costly, not what was asked), and a persisted `recommendations` table that
  drifts from the data it describes and needs a scheduler plus invalidation.
- **Decision:** Build the **AI Engineering Advisor** as a recommendation engine of **pure, per-domain
  analyzers** (`PromptAdvisor`, `ModelAdvisor`, `CostAdvisor`, `AgentAdvisor`, `RagAdvisor`) composed
  by `AdvisorService`. Recommendations are **computed on read** from current platform data and
  **never persisted** — consistent with how benchmark leaderboards and regression findings are
  derived — so they can never drift. Every recommendation carries the fixed shape **why / what
  changed / how to fix / expected improvement / confidence / severity / evidence**, plus an optional
  link to the knowledge graph. Heuristic thresholds live in `AdvisorProperties` (`broksforge.advisor.*`).
- **Status:** Accepted.
- **ADR:** [ADR 0011 — AI Engineering Advisor and the on-read recommendation model](./adr/0011-ai-engineering-advisor.md).

---

## FD-010 — Root-cause analysis is a pure engine over published evaluation reads

- **Date:** 2026-07-01
- **Context:** A failed `EvaluationResult` is red but not actionable; engineers need to know *why* a
  job failed or a regression occurred. The diagnosis logic must not bleed into the evaluation module
  or become a god class, and must be trustworthy (deterministic, not an LLM guess).
- **Decision:** A dedicated `rootcause` module with a **pure `RootCauseEngine`** (no I/O, like the
  metric engine) that classifies dominant failure modes from a job summary, a per-metric pass/fail
  tally, a bounded sample of failed runs, or a regression check. It returns `RootCauseFinding`s
  shaped exactly as requested — root cause, evidence, confidence, recommendation, expected
  improvement, severity — and links each to the knowledge graph. Data is loaded through **published
  services** (new evaluation reads: single run, failed-run sample, metric tally), so tenant scoping
  is enforced once in the owning modules.
- **Status:** Accepted.
- **ADR:** [ADR 0012 — Root-cause analysis engine](./adr/0012-root-cause-analysis-engine.md).

---

## FD-011 — The Engineering Knowledge Graph is relational reference data with a learning seam

- **Date:** 2026-07-01
- **Context:** The advisor and root-cause engines reason about the same recurring patterns (timeouts,
  prompt bloat, cost spikes, oversized chunks). That knowledge needs a home that is queryable,
  navigable, linkable by stable id, and ready to learn from real usage — without standing up a graph
  database for ~20 patterns.
- **Decision:** Model the **Engineering Knowledge Graph** relationally in the existing PostgreSQL:
  `knowledge_nodes` + `knowledge_edges`, seeded via Flyway (V24/V25) with ~20 canonical patterns and
  ~20 typed relations. It is **platform-global reference data** (no tenant columns), addressed by a
  stable `node_key` that findings link to. `occurrence_count`, incremented by a published
  `recordObservation`, is the **seam for future learning** — the platform begins recording which
  patterns actually occur.
- **Status:** Accepted.
- **ADR:** [ADR 0013 — Engineering Knowledge Graph](./adr/0013-engineering-knowledge-graph.md).

---

## FD-012 — The AI Debugger reconstructs a timeline now; tracing is a seam, exporters deferred

- **Date:** 2026-07-01
- **Context:** Engineers need to see *where* a run went wrong, across stages (prompt → memory →
  retriever → tools → model → parser → output). But the platform does not yet capture per-stage
  spans — only whole-invocation telemetry. Faking per-stage durations would be dishonest; waiting for
  distributed tracing would defer all value.
- **Decision:** The **AI Debugger** reconstructs a timeline from **persisted run data** over a fixed
  `ExecutionStage` vocabulary, marking stages the platform cannot yet see (memory, retriever, tools)
  as **`NOT_INSTRUMENTED`** rather than faking them. A dependency-free **`TraceRecorder` SPI** (no-op
  default) is the single drop-in point for live span recording and a future OpenTelemetry exporter —
  **no exporter is wired in Phase 4** (a deliberate scope boundary). When tracing lands, the same
  contract fills in the placeholder stages with real spans.
- **Status:** Accepted.
- **ADR:** [ADR 0014 — AI Debugger execution timeline and the tracing seam](./adr/0014-ai-debugger-and-tracing-seam.md);
  builds on [ADR 0010 — Observability and OpenTelemetry readiness](./adr/0010-observability-and-opentelemetry-readiness.md).

---

See also: [PROJECT_RULES.md](./PROJECT_RULES.md) · [ROADMAP.md](./ROADMAP.md) ·
[CONTRIBUTING.md](./CONTRIBUTING.md) · [ADRs](./adr/README.md)
