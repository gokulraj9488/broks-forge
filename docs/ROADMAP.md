# Roadmap — Brok's Forge

> **Brok's Forge — The Engineering Platform for AI Agents.**
> Modular monolith · Java 21 · Spring Boot 3.4.1 · PostgreSQL + Flyway · Redis · Next.js 15.

This roadmap tracks what is built, what is in flight, and where the platform is headed. Phases
are additive: each builds on the last without removing or simplifying existing capability
(see [PROJECT_RULES.md → ARCH-3](./PROJECT_RULES.md)). Flyway migrations are append-only;
the migration range owned by each phase is noted so the schema's history is traceable.

---

## Completed Phases

### Phase 1 — Foundation  ·  `v0.1.0`  ·  migrations `V1`..`V5`

The multi-tenant backbone every other module relies on.

- **Authentication & identity** — registration, login, JWT access/refresh tokens (jjwt),
  BCrypt password hashing, refresh/reset/verification token handling (SHA-256 hashed).
- **Users** — user profiles and account lifecycle.
- **Organizations** — the top-level tenant boundary, with membership and the
  **OWNER > ADMIN > MEMBER** role hierarchy enforced by `OrganizationAccessService`.
- **Projects** — the working scope nested under an organization; the unit most resources are
  scoped to.
- **API keys** — programmatic access with hashed key secrets and masked metadata responses.
- **Cross-cutting foundations** — `BaseEntity` (UUID PK, audit columns, optimistic `version`,
  soft delete), global `ApiError`/`ErrorCode` handling, `PageResponse<T>`, Bean Validation,
  correlation/request-ID propagation via MDC + headers, Spring Security wiring, and
  `ddl-auto=validate` schema discipline.

### Phase 2 — Agent Registry  ·  `v0.2.0`  ·  migrations `V6`..`V10`

`Agent` established as the central aggregate root of the platform
([ADR 0002](./adr/0002-agent-as-central-entity.md)).

- **Agent registry** — framework-neutral agent metadata (framework, language, endpoint URL,
  auth type, capabilities, `visibility`, open-ended `customMetadata`).
- **Agent versions** — deployment history as first-class data (model, provider, framework
  version, git SHA, prompt version, environment) with an active-version pointer; the join target
  for future evaluation/benchmark/trace results.
- **Agent credentials** — usage secrets stored **encrypted** with AES-256-GCM, write-only over
  the API, decrypted only for internal outbound calls
  ([ADR 0003](./adr/0003-credential-encryption-vs-hashing.md)).
- **Agent health checks** — outbound reachability probing protected by `OutboundUrlGuard` +
  `@ValidEndpointUrl` against SSRF ([ADR 0004](./adr/0004-ssrf-protection-for-agent-endpoints.md)).
- **Agent tags** — organization-scoped labeling and discovery.

### Phase 3 — The Intelligence Layer  ·  `v0.3.0`  ·  migrations `V11`..`V23`

The phase that turns a registry of agents into an **engineering platform**: the tooling to
measure, compare, and improve agents systematically. All P3 schema changes start at `V11`.

- **Dataset** — `Dataset`, `DatasetVersion`, `DatasetItem`. CSV + JSON import; **versioned and
  immutable** so any evaluation is reproducible against an exact dataset version; per-version
  statistics.
- **Prompt** — `Prompt`, `PromptVersion`. `{{variable}}` templating, full versioning, version
  **compare**, and **rollback** to a previous version.
- **Model (provider-agnostic SPI)** — a `ModelInvoker` SPI plus `AgentEndpointInvoker` as the
  concrete invocation target. Providers: **OpenAI, Anthropic, Groq, Ollama, Gemini, OpenRouter,
  DeepSeek**. Adding a provider is a code-only change behind the interface.
- **Evaluation** — the measurement engine. `EvaluationJob` is the **top-level** object →
  `EvaluationRun` → `EvaluationResult`, configured by an `EvaluationProfile`, scored by the
  `EvaluationMetricType` engine, with explicit lifecycle statuses. Built to **scale to millions**
  of results; executed synchronously today behind a queue-ready seam.
- **Benchmark** — `Benchmark`, `BenchmarkEntry`. Compare across Agent / Version / Prompt / Model /
  Dataset / Profile dimensions, with **leaderboards**.
- **Regression** — `RegressionCheck` comparing a **baseline vs. candidate** across latency, cost,
  quality, token usage, model, prompt, agent, and version.
- **Analytics** — cost, latency, token, and usage metrics with **trends** over time.
- **Report** — on-demand exports in **JSON / CSV / HTML**, structured to be **PDF-ready**.
- **Search** — global search across platform entities.
- **Dashboard** — the aggregated operational and quality view over the above.

**Exit criteria:** every P3 module passes the architecture + security self-review
([PROJECT_RULES.md → PROC-4](./PROJECT_RULES.md)); see [FEATURE_DECISIONS.md](./FEATURE_DECISIONS.md)
for the decisions that shaped this phase.

---

## Current Phase

### Phase 4 — The AI Engineering Advisor  ·  `v0.4.0`  ·  migrations `V24`..`V25`

The phase that turns a measurement platform into an **AI engineering advisor**: every feature now
answers an engineering question and emits an actionable recommendation — **why**, **what changed**,
**how to fix**, **expected improvement**, **confidence** and **severity**.

- **AI Engineering Advisor** — a recommendation engine (not a chatbot) composed of pure, per-domain
  advisors: **Prompt**, **Model**, **Cost**, **Agent** and **RAG**. Recommendations are computed
  **on read** from existing platform data and never persisted, so they never drift
  ([ADR 0011](./adr/0011-ai-engineering-advisor.md)).
- **Root-cause analysis** — a pure `RootCauseEngine` that explains *why* an evaluation failed or a
  regression occurred, as findings carrying root cause, evidence, confidence, recommendation,
  expected improvement and severity ([ADR 0012](./adr/0012-root-cause-analysis-engine.md)).
- **Engineering Knowledge Graph** — a persisted, platform-global catalogue of failure modes,
  regressions, recommendations and optimisations (nodes + typed edges), seeded with canonical
  patterns and carrying an occurrence counter as the seam for future learning
  ([ADR 0013](./adr/0013-engineering-knowledge-graph.md)).
- **AI Debugger** — a stage-by-stage **execution timeline** for a single run (prompt → memory →
  retriever → tools → model → parser → output), reconstructed from persisted run data, with
  uninstrumented stages reported honestly as `NOT_INSTRUMENTED`
  ([ADR 0014](./adr/0014-ai-debugger-and-tracing-seam.md)).
- **Observability / tracing seam** — an `ExecutionStage` vocabulary and a `TraceRecorder` SPI
  (no-op default) as the drop-in point for OpenTelemetry exporters. Architecture only; **no
  exporters are wired** (a deliberate scope boundary —
  [ADR 0010](./adr/0010-observability-and-opentelemetry-readiness.md)).

**Exit criteria:** every P4 module passes the architecture + security self-review; recommendations
and findings are derived on read; no Phase 1–3 behaviour is changed.

---

### V1 — GA hardening & the Benchmark Gallery  ·  `v1.0.0`

Shipped alongside the GA hardening line in the Version History table below — real engineering work
verified live against real infrastructure, not planned/future items:

- **Benchmark Gallery** — a new one-click onboarding feature on the `benchmark` module: 8 curated
  templates (Customer Support, RAG, Coding, Reasoning, Hallucination, Safety, Summarization,
  Translation), each provisioning a starter Dataset, Prompt, Evaluation Profile and an auto-run
  Evaluation Job in a single API call. Backed by a static, in-code catalog
  (`BenchmarkGalleryCatalog`) — **no new tables**, since it provisions ordinary entities through the
  existing dataset/prompt/evaluation services rather than persisting anything gallery-specific. See
  [MASTER_ARCHITECTURE.md → Benchmark Gallery](./MASTER_ARCHITECTURE.md#benchmark-gallery-v1--curated-one-click-templates).
- **Native Ollama trust model** — a narrow, explicit `OutboundUrlGuard` bypass so native Ollama
  providers can reach `localhost`/`127.0.0.1`/`host.docker.internal` without
  `BROKSFORGE_MODEL_ALLOW_PRIVATE_TARGETS=true`, asserted per call site from the provider's type,
  never inferred from the URL. Custom REST on localhost remains blocked; remote providers are
  unaffected. Ollama health checks now correctly probe `GET /api/tags` instead of `GET /api/chat`.
- **Evaluation model resolution** — a precedence chain (evaluation override → agent version override
  → the linked Provider's configured default model) that fixes the "HTTP 400 model is required"
  failure for Ollama-backed jobs, plus a defense-in-depth validation error (instead of a silently
  missing model field) when no model resolves and the endpoint requires one.
- **Provider Test Connection / Refresh Models UI** — `ProviderController`'s
  `POST /{providerId}/test-connection`, wired into the frontend Providers panel and the Agent Health
  page, surfaces exactly what was probed (method, URL, strategy) instead of a generic message.
- **Deployment architecture** — a documented Railway (backend) + Vercel (frontend) production
  deployment path, including the `server.port: ${PORT:8080}` change needed for Railway. See
  [DEPLOYMENT.md](./DEPLOYMENT.md).
- **Advisor bug fix** — `AgentAdvisor`'s three distinct finding types (insecure transport, missing
  auth, missing healthcheck) previously shared one `knowledgeKey`, corrupting knowledge-graph
  observation tracking; each now reports its own key.

---

## Remaining / Future Phases

### V1.1 — explicitly deferred, not started

Called out here so it is unambiguous that none of the following has been started — no partial
implementation, no design spike, nothing beyond the item existing as a candidate:

- **AI-generated test cases** — synthesizing dataset items / test cases from a description or an
  existing dataset, rather than hand-authoring them.
- **Production monitoring** — live monitoring of deployed agents beyond the existing on-demand health
  check and analytics views.
- **Quality-drop alerting** — proactive notification when quality/cost/latency regresses, distinct
  from the existing on-demand `RegressionCheck`.
- **CI/CD pipeline** — a pipeline definition/integration for running evaluations and regression gates
  automatically on push/deploy (related to, but not the same as, the CLI candidate in Phase 6).
- **Team collaboration features** — beyond the existing OWNER/ADMIN/MEMBER roles: comments,
  assignment, review workflows on evaluation results or advisor recommendations.
- **SSRF DNS-rebinding hardening** — `OutboundUrlGuard` resolves a hostname once at validation time
  and again when the HTTP client actually connects; an attacker-controlled DNS record could rebind
  between the two lookups. Deferred rather than rushed: closing it properly means pinning the
  resolved IP through the HTTP client stack across all seven call sites that use the guard, which
  needs dedicated testing time this pass did not have. See
  [MASTER_ARCHITECTURE.md → Native Ollama trust model](./MASTER_ARCHITECTURE.md#native-ollama-trust-model-narrow-explicit-bypass)
  for the current guard behavior this hardening would extend.

### Phase 5 — Live Tracing & RAG / Memory Inspectors  ·  target `v0.5.0`

Turn the Phase 4 seams into live signal.

- **OpenTelemetry exporters** wired to drive the `TraceRecorder` seam, lighting up the AI Debugger's
  `NOT_INSTRUMENTED` stages (memory, retriever, tools) with real spans and per-stage latency.
- **RAG inspector** — visibility into retrieval: queries, retrieved chunks, scores, and grounding,
  feeding the RAG advisor real data instead of declared configuration.
- **Memory inspector** — inspection of agent memory/state across a session or run.

### Phase 6 — SDK & CLI  ·  target `v0.6.0`

Meet engineers where they work.

- **SDK** — a typed client (starting with TypeScript, then Python) so agents can register, run
  evaluations, and report results programmatically.
- **CLI** — `broksforge` command-line tool for CI pipelines: run an evaluation, gate on a
  regression check, push a dataset/prompt version.

### Phase 7 — Async Eval Workers + Multi-Region  ·  target `v0.7.0`

Scale the Intelligence Layer for production volume and global users.

- **Async evaluation workers** — realize the queue-ready seam from P3: move
  `EvaluationJob` execution onto durable queues with horizontally scalable workers, retries, and
  backpressure, sustaining millions of `EvaluationResult` rows.
- **Multi-region** — region-aware data residency and routing, with the modular-monolith seams
  (id-only references, published services) used to split hot modules out where needed.
- **Operational hardening** — egress proxy / IP-pinning for SSRF defense-in-depth, envelope
  encryption with a KMS, and per-tenant data keys.

---

## Long-Term Vision

Brok's Forge aims to be the **system of record for AI-agent quality** — the place an engineering
team registers an agent, and from then on can answer, with evidence:

- *Is this version better than the last one?* (evaluation + benchmarks + leaderboards)
- *Did this change cause a regression in quality, latency, or cost?* (regression checks in CI)
- *Why did this specific run fail?* (tracing + AI debugger)
- *What is this agent costing us, and how is that trending?* (analytics)
- *Can I reproduce this result exactly, months later?* (immutable dataset & prompt versions)

The endgame is a closed loop — register, evaluate, benchmark, debug, ship, guard against
regressions — delivered through the UI, an SDK, and a CI-friendly CLI, while staying
framework- and provider-agnostic and strictly multi-tenant.

---

## Version History

| Version | Phase | Theme | Migrations | Status |
|---------|-------|-------|------------|--------|
| `0.1.0` | P1 | Foundation (auth, user, organization, project, API key) | `V1`..`V5` | Released |
| `0.2.0` | P2 | Agent Registry (versions, credentials, health, tags) | `V6`..`V10` | Released |
| `0.3.0` | P3 | Intelligence Layer (dataset, prompt, model, evaluation, benchmark, regression, analytics, report, search, dashboard) | `V11`..`V23` | Released |
| `0.4.0` | P4 | AI Engineering Advisor (advisor, root-cause, knowledge graph, AI debugger, tracing seam) | `V24`..`V25` | Released |
| `1.0.0` | GA | Production hardening & 1.0.0 release — Prometheus metrics, ECS structured logging, Kubernetes health probes, Redis-backed auth rate limiting, pluggable e-mail transport (ADR 0016) | — | Released |

---

## Future Features

Candidate work not yet committed to a phase:

- **Scheduled & continuous evaluation** — recurring evaluation jobs and on-deploy gates.
- **Alerting** — notify on regression-check failures, cost spikes, or health-check failures.
- **Human-in-the-loop scoring** — manual review and annotation feeding `EvaluationResult`.
- **LLM-as-judge metric pack** — additional `EvaluationMetricType` strategies using the model SPI.
- **Dataset synthesis & augmentation** — generate or expand datasets from existing data.
- **Prompt optimization** — guided/automated prompt iteration against a dataset.
- **Cost budgets & quotas** — per-organization spend limits surfaced from analytics.
- **Webhooks & integrations** — emit platform events to external systems and CI.
- **Public agent registry / sharing** — built on the existing agent `visibility` field.
- **Audit-log UI** — surface the existing audit trail for compliance review.
- **PDF report rendering** — realize the PDF-ready report structure (deferred from P3).
- **Role granularity** — finer-grained, resource-level permissions beyond OWNER/ADMIN/MEMBER.

See [PROJECT_RULES.md](./PROJECT_RULES.md), [FEATURE_DECISIONS.md](./FEATURE_DECISIONS.md),
[CONTRIBUTING.md](./CONTRIBUTING.md), and the [ADRs](./adr/README.md) for the rules and rationale
behind this roadmap.
