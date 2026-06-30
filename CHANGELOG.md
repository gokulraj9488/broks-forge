# Changelog

All notable changes to **Brok's Forge** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Nothing yet. In-flight work targets Phase 5 (live tracing & RAG / memory inspectors)._

## [1.0.0] - 2026-07-01

First general-availability release of Brok's Forge — the engineering platform for AI agents.
It bundles the four foundational phases (P1–P4) into a single, production-ready 1.0 line.
Flyway migrations `V1`..`V25` make up this release.

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

- **Dataset** — versioned, immutable datasets with CSV + JSON import and per-version statistics,
  so any evaluation is reproducible against an exact dataset version.
- **Prompt** — `{{variable}}` templating with full versioning, version compare, and rollback.
- **Model (provider-agnostic SPI)** — a `ModelInvoker` SPI with `AgentEndpointInvoker`; adding a
  provider is a code-only change behind the interface.
- **Evaluation** — the measurement engine: `EvaluationJob` -> `EvaluationRun` -> `EvaluationResult`,
  configured by an `EvaluationProfile`, scored by the metric-type engine, built to scale to millions
  of results (executed synchronously today behind a queue-ready seam).
- **Benchmark** — comparison across Agent / Version / Prompt / Model / Dataset / Profile dimensions,
  with leaderboards.
- **Regression** — baseline-vs-candidate checks across latency, cost, quality, token usage, and more.
- **Analytics** — cost, latency, token, and usage metrics with trends over time.
- **Report** — on-demand exports in JSON / CSV / HTML, structured to be PDF-ready.
- **Search** — global search across platform entities.
- **Dashboard** — the aggregated operational and quality view over the above.

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

### Security

- Multi-tenant isolation enforced via the `(id, projectId, organizationId)` ownership tuple
  (foreign id -> 404, no existence leak), RBAC in the service layer, and SSRF defense via
  `@ValidEndpointUrl` + `OutboundUrlGuard` on every outbound call. See
  [docs/SECURITY_GUIDE.md](./docs/SECURITY_GUIDE.md) for the full control set and known limitations.

[Unreleased]: https://github.com/your-org/broks-forge/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/your-org/broks-forge/releases/tag/v1.0.0
