# Changelog

All notable changes to **Brok's Forge** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Nothing yet. See [docs/ROADMAP.md → V1.1](./docs/ROADMAP.md) for what's next._

## [1.0.0] - 2026-07-14

First general-availability release of Brok's Forge — the engineering platform for AI agents.
Bundles the four foundational phases (P1–P4), the V1 Benchmark Gallery, native Ollama support,
and a professional design system refresh into a single, production-ready 1.0 line. Flyway
migrations `V1`..`V40` make up this release.

### Highlights

- **Benchmark Gallery** — 8 curated one-click templates (Customer Support, RAG, Coding,
  Reasoning, Hallucination, Safety, Summarization, Translation), each provisioning a starter
  dataset, prompt, and evaluation profile, then auto-running against your agent.
- **Native Ollama support** — Test Connection / Refresh Models work out of the box against a
  local Ollama instance, with a narrow, explicit SSRF-guard trust model (not a global bypass).
- **Provider abstraction** — register OpenAI, Groq, OpenRouter, Anthropic, Google AI Studio, or
  Ollama once per project and reference it from any agent, instead of duplicating credentials.
- **Professional design system** — a GitHub/Google/Vercel-inspired color palette, a shared table
  primitive, workflow-grouped sidebar navigation, and a polished dashboard.

### Added

#### Phase 1 — Foundation (migrations `V1`..`V5`)

- **Authentication & identity** — registration, login, JWT access/refresh tokens (jjwt) with
  refresh rotation, BCrypt password hashing, and SHA-256-hashed reset/verification tokens.
- **Users** — user profiles and account lifecycle.
- **Organizations** — the top-level tenant boundary with membership and the
  **OWNER > ADMIN > MEMBER** role hierarchy enforced by `OrganizationAccessService`.
- **Projects** — the working scope nested under an organization.
- **API keys** — programmatic access with SHA-256-hashed key secrets and masked metadata responses.
- **Cross-cutting foundations** — `BaseEntity` (UUID PK, audit columns, optimistic `version`,
  soft delete), global `ApiError`/`ErrorCode` handling, `PageResponse<T>`, Bean Validation,
  correlation/request-ID propagation, Spring Security wiring, and `ddl-auto=validate` discipline.

#### Phase 2 — Agent Registry (migrations `V6`..`V10`)

- **Agent registry** — framework-neutral agent metadata (framework, language, endpoint URL,
  auth type, capabilities, visibility, open-ended `customMetadata`); `Agent` is the central aggregate.
- **Agent versions** — deployment history as first-class data with an active-version pointer.
- **Agent credentials** — usage secrets stored **encrypted** (AES-256-GCM), write-only over the API,
  decrypted only for internal outbound calls.
- **Agent health checks** — outbound reachability probing.
- **Agent tags** — organization-scoped labeling and discovery.

#### Phase 3 — Intelligence Layer (migrations `V11`..`V23`)

- **Dataset** — versioned, immutable datasets with CSV/JSON/XLSX/ZIP import (with column-mapping
  preview) and per-version statistics, so any evaluation is reproducible against an exact version.
- **Prompt** — `{{variable}}` templating with full versioning, version compare, and rollback.
- **Model (provider-agnostic SPI)** — a `ModelInvoker` SPI with `AgentEndpointInvoker`, and
  per-provider adapters (OpenAI, Anthropic, Groq, OpenRouter, Google AI Studio, native Ollama).
- **Evaluation** — the measurement engine: `EvaluationJob` → `EvaluationRun` → `EvaluationResult`,
  configured by an `EvaluationProfile`, scored by a pluggable metric engine (exact match, contains,
  regex, JSON validity, length, semantic similarity, LLM judge, hallucination detection, citation
  verification, custom, latency/cost/token thresholds), with immutable profile versioning and a
  background execution engine (checkpointing, cancellation, resume) for large jobs.
- **Benchmark** — comparison across Agent / Version / Prompt / Model / Dataset / Profile dimensions,
  with leaderboards.
- **Benchmark Gallery** — see Highlights above.
- **Regression** — baseline-vs-candidate checks across latency, cost, quality, token usage, and more.
- **Analytics** — cost, latency, token, and usage metrics with trends over time.
- **Report** — on-demand exports in JSON / CSV / HTML, structured to be PDF-ready.
- **Search** — global search across platform entities.
- **Dashboard** — quick actions, recent evaluations/benchmarks/agents, provider health, and
  evaluation success rate.

#### Phase 4 — AI Engineering Advisor (migrations `V24`..`V25`)

- **AI Engineering Advisor** — a recommendation engine (Prompt, Model, Cost, Agent, RAG advisors)
  computed on read from existing data and never persisted, so recommendations never drift.
- **Root-cause analysis** — a pure `RootCauseEngine` explaining *why* an evaluation failed or a
  regression occurred, with evidence, confidence, recommendation, expected improvement, and severity.
- **Engineering Knowledge Graph** — a persisted, platform-global catalogue of failure modes,
  regressions, recommendations, and optimisations (nodes + typed edges), seeded with canonical
  patterns and carrying an occurrence counter as a learning seam.
- **AI Debugger** — a stage-by-stage execution timeline for a single run, reconstructed from
  persisted run data, with uninstrumented stages reported honestly as `NOT_INSTRUMENTED`.
- **Observability / tracing seam** — an `ExecutionStage` vocabulary and a `TraceRecorder` SPI
  (no-op default) as the drop-in point for future OpenTelemetry exporters.
- **Metrics & structured logging** — a Prometheus registry exposing an ADMIN-guarded
  `/actuator/prometheus` endpoint for scraping, plus structured, correlation-ID-aware logging.

#### V1 — Provider abstraction, Benchmark Gallery, and native Ollama (migrations `V26`..`V40`)

- **Provider abstraction** — register a provider once per project (OpenAI, Groq, OpenRouter,
  Anthropic, Google AI Studio, Ollama, custom), test its connection, and refresh its available
  models, instead of duplicating connection details on every agent.
- **Benchmark Gallery** — 8 curated templates provisioning a dataset, prompt, and evaluation
  profile in one call.
- **Dataset uploads** — CSV/JSON/XLSX/ZIP file uploads with column-mapping preview and history.
- **Evaluation job events & background execution** — checkpointed, resumable, cancellable
  execution for evaluations too large to run synchronously.

### Fixed

- **Native Ollama SSRF trust model** — health checks now correctly probe `GET /api/tags` instead
  of `GET /api/chat` (which returned HTTP 405); a narrow, explicit `OutboundUrlGuard` bypass lets
  native Ollama providers reach `localhost`/`127.0.0.1`/`host.docker.internal` without disabling
  SSRF protection globally — Custom REST providers on localhost remain blocked.
- **Evaluation model resolution** — a precedence chain (evaluation override → agent version
  override → provider default model) fixes "HTTP 400 model is required" for Ollama-backed jobs.
- **Advisor knowledge keys** — three distinct `AgentAdvisor` finding types no longer share one
  knowledge-graph key.
- **Cross-organization analytics leak** — job counts were scoped by project only; now scoped by
  organization and project.
- **Archived-resource error codes** — dataset/prompt archive conflicts no longer report
  `AGENT_ARCHIVED`; they have their own `DATASET_ARCHIVED`/`PROMPT_ARCHIVED` codes.
- **Background vs. synchronous evaluation summaries** — large jobs run via the background engine
  now publish the exact same summary shape (per-metric breakdown, execution health) as small
  synchronously-run jobs.

### Changed

- **Design system** — a GitHub/Google/Vercel-inspired color palette (Google Blue primary, GitHub
  Green success, Google Amber warning, Material Red danger), a shared `Table` primitive with
  sticky headers, workflow-grouped sidebar navigation (Workspace / Build / Evaluate / Observe /
  Settings), and a polished dashboard. Presentational only — no API contract changes.
- **`server.port`** now reads `${PORT:8080}` so Railway's injected `PORT` works; Docker Compose is
  unaffected since it never sets `PORT`.

### Security

- Multi-tenant isolation enforced via the `(id, projectId, organizationId)` ownership tuple
  (foreign id → 404, no existence leak), RBAC in the service layer, and SSRF defense via
  `@ValidEndpointUrl` + `OutboundUrlGuard` on every outbound call. See
  [docs/SECURITY_GUIDE.md](./docs/SECURITY_GUIDE.md) for the full control set and known limitations.

### Known Limitations

See [docs/ROADMAP.md → V1.1](./docs/ROADMAP.md) for the full list, including SSRF DNS-rebinding
hardening, a dataset version-numbering race, and missing advisor/regression unit tests.

[Unreleased]: https://github.com/gokulraj9488/broks-forge/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/gokulraj9488/broks-forge/releases/tag/v1.0.0
