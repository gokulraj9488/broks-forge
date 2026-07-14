# Brok's Forge v1.0.0 — Release Notes

**The Engineering Platform for AI Agents.**
_Released 2026-07-14 · Java 21 / Spring Boot 3.4.1 · Next.js 15 / React 19 · PostgreSQL 16 + Flyway · Redis 7_

## What is Brok's Forge

Brok's Forge is a multi-tenant platform that turns a registry of AI agents into a full
engineering workflow: register an agent, measure it against a dataset, compare versions and
models, debug a failing run, and guard against regressions — all strictly multi-tenant, and
framework- and provider-agnostic. Version 1.0.0 is the first general-availability release.

---

## Core features

- **Agent Registry** — framework-neutral agent metadata, deployment versions with activate/
  rollback, AES-256-GCM-encrypted usage credentials, and health checks.
- **Provider abstraction** — register a provider (OpenAI, Groq, OpenRouter, Anthropic, Google AI
  Studio, or native Ollama) once per project and reference it from any agent — test its
  connection and refresh its available models directly from the UI.
- **Datasets & Prompts** — versioned, immutable datasets (CSV/JSON/XLSX/ZIP import with
  column-mapping preview) and a versioned prompt library with `{{variable}}` templating,
  activate/rollback, and version comparison.
- **Evaluation engine** — `EvaluationJob` → `EvaluationRun` → `EvaluationResult`, scored by a
  pluggable metric engine, with immutable evaluation-profile versioning and a background
  execution engine (checkpointing, cancellation, resume) for datasets too large to run
  synchronously.
- **Benchmark Gallery** — see below.
- **Benchmarking & regression** — compare agents/versions/prompts/models/datasets/profiles with
  leaderboards, and catch baseline-vs-candidate regressions in latency, cost, quality, and tokens.
- **AI Engineering Advisor** — a recommendation engine (Prompt, Model, Cost, Agent, RAG advisors),
  root-cause analysis for failed evaluations and regressions, an Engineering Knowledge Graph, and
  a stage-by-stage AI Debugger execution timeline.
- **Operate with confidence** — analytics with trends, JSON/CSV/HTML reports, global search, a
  dashboard with quick actions and provider health, and an ADMIN-guarded Prometheus endpoint.

---

## Supported providers

| Provider | Native or OpenAI-compatible | Notes |
|---|---|---|
| **OpenAI** | Native adapter | `/chat/completions` |
| **Anthropic** | Native adapter | `/messages` |
| **Google AI Studio (Gemini)** | Native adapter | Model embedded in the URL path |
| **Groq** | OpenAI-compatible | |
| **OpenRouter** | OpenAI-compatible | |
| **DeepSeek** | OpenAI-compatible | |
| **Ollama** | Native adapter | Trusted for `localhost`/`127.0.0.1`/`host.docker.internal` without extra config — see [docs/DEPLOYMENT.md](./docs/DEPLOYMENT.md) |
| **Custom REST** | Generic envelope | Any agent's own wrapper endpoint |

Adding a provider is a code-only change behind the `ModelInvoker`/`ProviderAdapter` interfaces —
no other provider's behavior changes.

## Supported metrics

`EXACT_MATCH` · `CONTAINS` · `REGEX_MATCH` · `JSON_VALID` · `NON_EMPTY` · `LENGTH` ·
`SEMANTIC_SIMILARITY` · `LLM_JUDGE` · `HALLUCINATION_DETECTION` · `CITATION_VERIFICATION` ·
`CUSTOM` (dispatches to a named `CustomMetricEvaluator` bean — the no-enum-change extension
point) · `LATENCY` · `COST` · `TOKEN_COUNT`.

## Benchmark Gallery

Eight curated templates remove the "blank project" problem for new users. Each provisions a
starter dataset, prompt, and evaluation profile with recommended metrics, then runs an evaluation
against your chosen agent in one click:

| Template | Difficulty | Est. runtime |
|---|---|---|
| Customer Support | Easy | ~2 min |
| RAG | Medium | ~3 min |
| Coding | Medium | ~2 min |
| Reasoning | Medium | ~2 min |
| Hallucination | Medium | ~2 min |
| Safety | Hard | ~2 min |
| Summarization | Easy | ~2 min |
| Translation | Easy | ~2 min |

Templates using judge-family metrics (LLM Judge, Hallucination Detection, Citation Verification,
Semantic Similarity) ask you to pick a judge/embedding provider at import time — everything
provisioned is an ordinary, fully-editable Dataset/Prompt/Profile afterward.

---

## Deployment targets

- **Docker Compose** — the reference environment; `docker compose up --build` orchestrates the
  backend, frontend, PostgreSQL, and Redis.
- **AWS EC2** (backend, Docker Compose + Nginx + Let's Encrypt) + **Vercel** (frontend) — the
  production path. See [docs/AWS_EC2_SETUP.md](./docs/AWS_EC2_SETUP.md) for server bring-up and
  [docs/DEPLOYMENT.md](./docs/DEPLOYMENT.md) for the full deployment flow, required environment
  variables, and a post-deploy verification checklist.

---

## Install & upgrade

This is the first release, so there is nothing to upgrade from — a clean install:

### 1. Prerequisites

- **Docker** + Docker Compose (PostgreSQL 16 and Redis 7 are orchestrated for you).
- `openssl` to generate secrets.
- For local (non-container) development: **JDK 21** and **Node.js 20+**.

### 2. Configure the required environment

Copy the example file and set the two required Base64 secrets. They **must differ**:

```bash
cp .env.example .env

# JWT signing secret (HS256, >= 32 bytes)
openssl rand -base64 48      # -> JWT_SECRET

# AES-256-GCM credential-encryption key (exactly 32 bytes)
openssl rand -base64 32      # -> ENCRYPTION_KEY
```

The frontend is built with the public build arg **`NEXT_PUBLIC_API_BASE_URL`**
(defaults to `http://localhost:8080`).

> Losing `ENCRYPTION_KEY` makes stored agent credentials unrecoverable. Treat it as a long-lived
> secret and source it from a secrets manager in production.

### 3. Start the stack

```bash
docker compose up --build
```

On startup, Flyway applies migrations `V1`..`V40` and the backend boots under
`ddl-auto=validate`. Once up:

- **Web app:** <http://localhost:3000>
- **API + OpenAPI UI:** <http://localhost:8080>
- **Health:** <http://localhost:8080/actuator/health>
- **Metrics (ADMIN-guarded):** `GET /actuator/prometheus`

---

## Required accounts / keys

**None beyond the two secrets above.** Brok's Forge ships provider-agnostic: no third-party API
key is required to run the platform, and native Ollama support means you can evaluate against a
local model with zero API keys at all. You only supply a provider credential when you actually
register a provider that calls a hosted service — those secrets are stored encrypted
(AES-256-GCM) and are write-only over the API.

---

## Known limitations

These are accepted, documented trade-offs for 1.0, each with a path forward
(see [docs/SECURITY_GUIDE.md §15](./docs/SECURITY_GUIDE.md#15-known-limitations--hardening-roadmap)
and the [Roadmap](./docs/ROADMAP.md)):

- **SSRF guard does not fully stop DNS rebinding** — the guard resolves a hostname once at
  validation and again when the HTTP client connects; IP pinning through the client stack is the
  planned defense-in-depth (deferred rather than rushed across all seven call sites).
- **No telemetry exporters wired** — the `TraceRecorder` SPI and `ExecutionStage` vocabulary
  exist, but no OpenTelemetry exporters are connected yet, so some AI Debugger stages report
  `NOT_INSTRUMENTED`. Live tracing arrives in Phase 5.
- **Dataset version-numbering race** — concurrent version imports on the same dataset aren't
  protected by an optimistic lock.
- **No unit tests for advisor/regression business logic** — covered by integration tests, but the
  core scoring math itself lacks dedicated unit coverage.
- **Encryption key in process env** — production should move to KMS / envelope encryption; the
  versioned ciphertext format already supports key rotation.

---

## Documentation & links

- [README and docs index](./docs)
- [Deployment Guide](./docs/DEPLOYMENT.md) · [Master Architecture](./docs/MASTER_ARCHITECTURE.md)
- [Contributing](./CONTRIBUTING.md) · [Code of Conduct](./CODE_OF_CONDUCT.md) · [Support](./SUPPORT.md)
- [Security Policy](./SECURITY.md) · [Security Guide](./docs/SECURITY_GUIDE.md)
- [Changelog](./CHANGELOG.md) · [Roadmap](./docs/ROADMAP.md)

---

## License

Brok's Forge is released under the [Apache License 2.0](./LICENSE).
Copyright 2026 Brok's Forge contributors.
