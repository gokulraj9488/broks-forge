<div align="center">

# ⚒️ Brok's Forge

### The Engineering Platform for AI Agents.

A production-grade, open-source foundation for building, shipping and operating AI agents.
Modular monolith · Clean Architecture · Built to scale into microservices.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.0-success.svg)](docs/ROADMAP.md)
[![backend-ci](https://img.shields.io/badge/backend--ci-passing-brightgreen.svg)](https://github.com/your-org/broks-forge/actions)
[![frontend-ci](https://img.shields.io/badge/frontend--ci-passing-brightgreen.svg)](https://github.com/your-org/broks-forge/actions)
[![CodeQL](https://img.shields.io/badge/CodeQL-passing-brightgreen.svg)](https://github.com/your-org/broks-forge/actions)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.4](https://img.shields.io/badge/Spring_Boot-3.4-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Next.js 15](https://img.shields.io/badge/Next.js-15-000000.svg)](https://nextjs.org/)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1.svg)](https://www.postgresql.org/)

</div>

---

> **Status — Version 1.0.0 (release-ready).**
> All four phases are delivered: **Foundation** (auth, organizations, projects, API keys, RBAC) ·
> **Agent Registry** (versioned agents, encrypted credentials, SSRF-guarded health) ·
> **Intelligence Layer** (datasets, prompts, provider-agnostic evaluation, benchmarking, regression,
> analytics, reports, search, dashboard) · **AI Engineering Advisor** (recommendations, root-cause
> analysis, AI debugger, knowledge graph). Hardened for production with Prometheus metrics, structured
> logging, health probes, CI/CD and full documentation. Async evaluation workers, live distributed
> tracing, and an SDK/CLI are the post-1.0 roadmap. See [docs/ROADMAP.md](docs/ROADMAP.md).

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Folder structure](#folder-structure)
- [Installation guide](#installation-guide)
- [Quick start (Docker)](#quick-start-docker)
- [Local development](#local-development)
- [Development workflow](#development-workflow)
- [Environment variables](#environment-variables)
- [API & Swagger](#api--swagger)
- [Security model](#security-model)
- [Observability](#observability)
- [Screenshots](#screenshots)
- [Verifying the build](#verifying-the-build)
- [Troubleshooting](#troubleshooting)
- [FAQ](#faq)
- [Roadmap](#roadmap)

---

## Features

**Authentication & accounts**
- Register, login, JWT access tokens + rotating refresh tokens, logout
- Forgot-password and password-reset flows (single-use, hashed tokens)
- Email-verification architecture (single-use, hashed tokens)
- Change password (revokes all sessions)
- BCrypt password hashing, strong-password policy

**Multi-tenancy**
- Organizations with slugs, owners and lifecycle status
- Organization members with role-based access control (`OWNER` / `ADMIN` / `MEMBER`)
- Projects scoped to organizations
- Project-scoped API keys (hashed at rest, shown once)

**Agent Registry (Phase 2) — the central platform entity**
- Framework-agnostic agents (Spring AI, LangGraph, CrewAI, AutoGen, PydanticAI, Semantic Kernel, custom REST, …)
- Versioned deployments with activate / rollback and one active version per agent
- Encrypted credentials at rest (AES-256-GCM) — never returned, never logged
- Manual health checks with latency, HTTP status, rolling availability % (scheduler-ready)
- Capabilities metadata (streaming, memory, RAG, tool-calling, …) + open custom metadata
- Tags, full-text search and filtering, pagination

**Intelligence Layer (Phase 3)**
- **Datasets** — immutable, versioned evaluation data with CSV/JSON import, validation, statistics, tags, search
- **Prompts** — a versioned prompt library with `{{variables}}`, activate/rollback and version comparison
- **Provider-agnostic model invocation** — a `ModelInvoker` SPI (OpenAI, Anthropic, Groq, Ollama, Gemini, OpenRouter, DeepSeek, …) with a key-free agent-endpoint invoker as the default execution target
- **Evaluation** — `EvaluationJob` is the top-level aggregate → many runs → many metric results, with reusable profiles, a pluggable metric engine, statuses and a precomputed summary (architected to scale to millions)
- **Benchmarking** — compare agents / versions / prompts / models / datasets / profiles and render leaderboards
- **Regression detection** — latency / cost / quality / token regressions of a candidate vs a baseline job
- **Analytics** — cost, latency, token and usage analytics with daily trends
- **Reports** — JSON / CSV / HTML exports (PDF-ready), with CSV-injection & XSS-safe rendering
- **Global search** and a **project dashboard** roll-up

**AI Engineering Advisor (Phase 4)**
- **AI Engineering Advisor** — a recommendation engine (not a chatbot) of pure per-domain advisors (Prompt, Model, Cost, Agent, RAG); every recommendation explains **why**, **what changed**, **how to fix**, **expected improvement**, **confidence** and **severity**, computed on read so it never drifts
- **Root-cause analysis** — explains *why* an evaluation failed or a regression occurred, as findings with root cause, evidence, confidence, recommendation, expected improvement and severity
- **Engineering Knowledge Graph** — a seeded, navigable catalogue of failure modes, regressions, recommendations and optimisations (nodes + typed edges) with an occurrence counter as the seam for future learning
- **AI Debugger** — a stage-by-stage execution timeline for a single run (prompt → memory → retriever → tools → model → parser → output), reconstructed from persisted data; uninstrumented stages are reported honestly
- **Observability / tracing seam** — an `ExecutionStage` vocabulary and `TraceRecorder` SPI as the drop-in point for OpenTelemetry (architecture only; no exporters wired yet)

> 📚 **Engineering docs** live in [`docs/`](docs/) — start with [MASTER_ARCHITECTURE.md](docs/MASTER_ARCHITECTURE.md) and [ENGINEERING_HANDBOOK.md](docs/ENGINEERING_HANDBOOK.md), then [PROJECT_RULES.md](docs/PROJECT_RULES.md) and the [ADRs](docs/adr/).

**Platform**
- Global exception handling with a consistent error contract
- Request validation, CORS, security headers, RBAC, tenant isolation, IDOR & SSRF protection
- Correlation/request IDs on every request and log line (OpenTelemetry-ready)
- Health checks (custom + Spring Actuator)
- OpenAPI / Swagger UI for every endpoint
- Flyway database migrations, UUID keys, full audit columns, soft deletes, check constraints
- Redis wired in and ready for caching / token revocation

**Web app**
- Login, Register, Forgot/Reset password, Email verification
- Dashboard, Organizations (+ members & settings), Projects, Agents (search/filter/pagination)
- Agent detail with Overview / Versions / Health / Credentials / Settings tabs
- Datasets, Prompts, Evaluations, Benchmarks and Analytics workspaces with detail views (Phase 3)
- Advisor and Knowledge workspaces, per-run AI Debugger timeline, and root-cause panels on evaluations (Phase 4)
- Dark-first design inspired by Linear / Vercel / Cursor, fully responsive
- Protected routes, transparent token refresh, optimistic data layer

---

## Tech stack

| Layer        | Technologies |
|--------------|--------------|
| **Backend**  | Java 21, Spring Boot 3.4, Spring Security, Spring Data JPA, Spring Validation, Spring Actuator, Spring AI (dependency), Flyway, PostgreSQL, Redis, JWT (jjwt), BCrypt, Lombok, MapStruct, springdoc OpenAPI |
| **Frontend** | Next.js 15 (App Router), React 19, TypeScript, TailwindCSS, shadcn-style UI (Radix), TanStack Query, Zustand, React Hook Form, Zod |
| **Infra**    | Docker, Docker Compose, PostgreSQL 16, Redis 7 |

---

## Architecture

Brok's Forge is a **modular monolith** built with **Clean Architecture**, **SOLID** and
**Domain-Driven Design** where it earns its keep.

- **Feature modules** (`auth`, `user`, `organization`, `project`, `apikey`, `agent`) live under
  `com.broksforge.modules`. Each owns its domain, repositories, services and web layer.
- See [`docs/adr/`](docs/adr/) for the Architecture Decision Records (modular monolith, agent as
  central entity, credential encryption, SSRF protection).
- **Modules reference each other by published service APIs and by id**, never by reaching into
  another module's persistence model — so any module can later be extracted into its own
  microservice with minimal change.
- **Cross-cutting concerns** (`common`, `config`, `security`) are shared infrastructure.
- The **security layer** authenticates JWTs and API keys via small ports
  (`ApiKeyAuthenticator`) so it never depends on a feature module's internals.

```
            ┌──────────────────────────────────────────────┐
            │                 Web (REST)                   │
            │  Controllers · DTOs · MapStruct · OpenAPI    │
            ├──────────────────────────────────────────────┤
            │              Application services             │
            │   AuthService · OrganizationService · …       │
            ├──────────────────────────────────────────────┤
            │                   Domain                      │
            │     Entities · Enums · Invariants            │
            ├──────────────────────────────────────────────┤
            │              Infrastructure                   │
            │  JPA repos · Security · Redis · Flyway        │
            └──────────────────────────────────────────────┘
```

> 📐 **Visual reference:** a consolidated, portfolio-grade set of **Mermaid** diagrams —
> system architecture, component map, ER model, deployment topology, and the request / evaluation /
> agent / advisor flow sequences — lives in [`docs/DIAGRAMS.md`](docs/DIAGRAMS.md).

---

## Folder structure

```
broks-forge/
├── docker-compose.yml          # postgres · redis · backend · frontend
├── .env.example                # copy to .env
├── docs/                       # MASTER_ARCHITECTURE, ENGINEERING_HANDBOOK, PROJECT_RULES, ROADMAP,
│   └── adr/                     #   CODING/SECURITY/TESTING/PERFORMANCE/API/ERROR guides + ADRs (0001–0016)
│
├── backend/                    # Spring Boot 3 / Java 21
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/broksforge/
│       │   ├── BroksForgeApplication.java
│       │   ├── common/         # base entities, auditing, exceptions, web, util, validation,
│       │   │                   #   security (encryption, SSRF guard), observability, persistence
│       │   ├── config/         # security, CORS, OpenAPI, Redis, observability, @ConfigurationProperties
│       │   ├── security/       # JWT + API-key auth, RBAC, entry points
│       │   └── modules/
│       │       ├── user/   organization/   project/   apikey/   system/
│       │       ├── agent/      # Phase 2: Agent, AgentVersion, AgentCredential, AgentHealthCheck, AgentTag
│       │       ├── ── Phase 3 (Intelligence Layer) ──
│       │       │   dataset/   prompt/   model/   evaluation/   benchmark/
│       │       │   regression/   analytics/   report/   search/   dashboard/
│       │       └── ── Phase 4 (AI Engineering Advisor) ──
│       │           advisor/   rootcause/   debugger/   knowledge/
│       └── resources/
│           ├── application*.yml
│           └── db/migration/   # Flyway V1…V25  (V11–V23 intelligence · V24–V25 knowledge graph)
│
└── frontend/                   # Next.js 15 / React 19 / TS
    ├── Dockerfile
    ├── package.json
    └── src/
        ├── app/                # App Router: (auth), (dashboard: agents, datasets, prompts,
        │                       #   evaluations, benchmarks, analytics, advisor, knowledge), verify-email
        ├── components/         # ui/ (shadcn-style), layout/, per-module dialogs & panels
        └── lib/                # api client, hooks (TanStack Query), store (Zustand), zod
```

---

## Installation guide

There are two supported ways to run Brok's Forge: the **all-in-one Docker stack** (recommended — runs
Postgres, Redis, the API and the web app together) or a **local dev** setup where you run each side
yourself. Pick one.

### Prerequisites

| Path | What you need |
|------|---------------|
| **Docker (recommended)** | [Docker Desktop](https://www.docker.com/products/docker-desktop/) with **Docker Compose v2** (`docker compose version`) |
| **Local dev** | **JDK 21**, **Maven 3.9+**, **Node.js 20+**, and a reachable **PostgreSQL 16** + **Redis 7** (you can still start just the datastores with Docker) |

### 1. Clone

```bash
git clone https://github.com/your-org/broks-forge.git
cd broks-forge
```

### 2. Configure the environment

```bash
cp .env.example .env
```

### 3. Set the two required secrets

The API **fails fast on boot** if either is missing — this is intentional (nothing is hardcoded). Both
are Base64 values; generate them with `openssl`:

```bash
openssl rand -base64 48   # -> paste as JWT_SECRET=<value>     (HS256 signing secret, ≥ 256 bits)
openssl rand -base64 32   # -> paste as ENCRYPTION_KEY=<value> (AES-256-GCM key, exactly 32 bytes)
```

Edit `.env` and set both `JWT_SECRET` and `ENCRYPTION_KEY`. The frontend's API base URL
(`NEXT_PUBLIC_API_BASE_URL`) defaults to `http://localhost:8080`; change it only if the API is reached
under a different origin. See [Environment variables](#environment-variables) for the full list.

### 4. Build and start

```bash
docker compose up --build
```

The first build compiles the backend and frontend images; subsequent starts are fast. Compose waits
for Postgres and Redis health checks before starting the API, and the API runs Flyway migrations
(`V1`…`V25`) on boot before serving traffic. When the stack is healthy, jump to
[Quick start (Docker)](#quick-start-docker) for the service URLs and a first walkthrough, or to
[Verifying the build](#verifying-the-build) for the full capability checklist.

> Prefer to run the pieces yourself? Skip Docker for the app tier and follow
> [Local development](#local-development) instead — you can still bring up only the datastores with
> `docker compose up postgres redis`.

---

## Quick start (Docker)

**Prerequisites:** Docker Desktop (with Docker Compose v2).

```bash
# 1. Configure environment
cp .env.example .env

# 2. Set the REQUIRED secrets in .env (the API fails fast without them):
openssl rand -base64 48   # -> paste as JWT_SECRET=<value>
openssl rand -base64 32   # -> paste as ENCRYPTION_KEY=<value>  (Phase 2; must be 32 bytes)

# 3. Build and start everything
docker compose up --build
```

Then open:

| Service          | URL                                   |
|------------------|---------------------------------------|
| Web app          | http://localhost:3000                 |
| API              | http://localhost:8080                 |
| Swagger UI       | http://localhost:8080/swagger-ui.html |
| Health           | http://localhost:8080/actuator/health |

Stop with `Ctrl+C`; tear down with `docker compose down` (add `-v` to wipe data volumes).

---

## Local development

You can run each side independently without Docker.

### Backend

**Prerequisites:** JDK 21, Maven 3.9+, and a PostgreSQL 16 + Redis 7 reachable on localhost.

```bash
cd backend
# Uses the `dev` profile by default (convenient localhost defaults + dev-only JWT secret)
mvn spring-boot:run
```

> Bring up just the datastores with Docker if you don't have them locally:
> `docker compose up postgres redis`

### Frontend

**Prerequisites:** Node.js 20+.

```bash
cd frontend
cp .env.example .env.local        # NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
npm install
npm run dev                        # http://localhost:3000
```

---

## Development workflow

Contributions follow a short, conventional loop. The full guide — coding standards, the per-module
"must test" gate, and the PR checklist — is in [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md).

1. **Branch off `main`.** Use a descriptive prefix, e.g. `feat/agent-health-scheduler`,
   `fix/eval-summary-rounding`, `docs/observability`.

2. **Make focused changes.** Keep modules independent — call other modules only through their
   **published services and by id** (see [Architecture](#architecture)). Schema changes are an
   **append-only** new Flyway migration; never edit an applied one.

3. **Run the tests** (mirrors what CI runs — see [`docs/TESTING_STRATEGY.md`](docs/TESTING_STRATEGY.md)):

   ```bash
   # Backend — unit + slice + Testcontainers integration (Docker must be running)
   cd backend && mvn -q -B verify

   # Frontend — lint + type-check + component tests
   cd frontend && npm run lint && npm test
   ```

4. **Use conventional commits.** Messages follow
   [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `refactor:`,
   `test:`, `chore:`, `perf:` (optionally scoped, e.g. `feat(advisor): …`). This keeps history readable
   and changelog-friendly.

5. **Open a pull request.** Fill in the description, link any issue, and ensure the CI checks
   (`backend-ci`, `frontend-ci`, `codeql`, `docker`, `dependency-review`) are green. New modules must
   satisfy the testing gate before merge.

---

## Environment variables

All secrets are read from the environment — nothing is hardcoded. See [`.env.example`](.env.example).

| Variable | Description | Default |
|----------|-------------|---------|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Database name / user / password | `broksforge` |
| `POSTGRES_PORT` | Host port for Postgres | `5432` |
| `REDIS_PORT` / `REDIS_PASSWORD` | Redis port / optional password | `6379` / *(empty)* |
| `BACKEND_PORT` | Host port for the API | `8080` |
| `JWT_SECRET` | **Required.** Base64 HS256 secret ≥ 256 bits | *(none)* |
| `ENCRYPTION_KEY` | **Required (Phase 2).** Base64 32-byte AES-256 key for agent credentials | *(none)* |
| `AGENT_HEALTH_ALLOW_PRIVATE_TARGETS` | Allow health probes to private/loopback agent URLs (SSRF) | `false` |
| `MODEL_ALLOW_PRIVATE_TARGETS` | Allow evaluation/model calls to private/loopback agent URLs (SSRF) | `false` |
| `EVALUATION_MAX_ITEMS_PER_JOB` | Max dataset rows a single synchronous evaluation job may run | `500` |
| `ADVISOR_PROMPT_MAX_CHARS` | *(Phase 4, optional)* Prompt length above which the advisor flags bloat | `8000` |
| `ADVISOR_PROMPT_MAX_VARIABLES` | *(Phase 4, optional)* Variable count above which a prompt is flagged complex | `12` |
| `ADVISOR_LATENCY_SPIKE_MS` | *(Phase 4, optional)* Avg latency (ms) above which the advisor flags a latency concern | `12000` |
| `ADVISOR_MIN_SAMPLES_FOR_COMPARISON` | *(Phase 4, optional)* Min comparable jobs before model/cost advice is emitted | `3` |
| `ADVISOR_FAILURE_SAMPLE_SIZE` | *(Phase 4, optional)* Max failed runs the root-cause engine samples per job | `50` |
| `BROKSFORGE_SECURITY_RATE_LIMIT_ENABLED` | Redis-backed per-IP rate limiting on auth endpoints | `true` |
| `BROKSFORGE_SECURITY_RATE_LIMIT_LIMIT` / `_WINDOW_SECONDS` | Requests allowed per window / window length | `20` / `60` |
| `SPRING_MAIL_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | **Prod only** (`SPRING_PROFILES_ACTIVE=prod`). SMTP server for real e-mail. Dev uses the console transport | *(none)* / `587` |
| `BROKSFORGE_MAIL_FROM_ADDRESS` / `_FROM_NAME` | *(Prod)* From identity for outbound e-mail | `no-reply@broksforge.dev` / `Brok's Forge` |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | Access-token lifetime | `900000` (15 min) |
| `JWT_REFRESH_TOKEN_EXPIRATION_MS` | Refresh-token lifetime | `2592000000` (30 d) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:3000` |
| `APP_PUBLIC_URL` | Base URL used in email links | `http://localhost:3000` |
| `FRONTEND_PORT` | Host port for the web app | `3000` |
| `NEXT_PUBLIC_API_BASE_URL` | API base URL used by the browser | `http://localhost:8080` |

---

## API & Swagger

Every endpoint is documented and explorable at **http://localhost:8080/swagger-ui.html**.

| Area | Method & path |
|------|---------------|
| Auth | `POST /api/v1/auth/register` · `login` · `refresh` · `logout` · `change-password` · `forgot-password` · `reset-password` · `verify-email` · `resend-verification` |
| Profile | `GET /api/v1/users/me` · `PATCH /api/v1/users/me` |
| Organizations | `GET/POST /api/v1/organizations` · `GET/PATCH/DELETE /api/v1/organizations/{id}` |
| Members | `GET/POST /api/v1/organizations/{id}/members` · `PATCH/DELETE …/members/{userId}` |
| Projects | `GET/POST /api/v1/organizations/{id}/projects` · `GET/PATCH/DELETE …/projects/{projectId}` |
| API keys | `GET/POST …/projects/{projectId}/api-keys` · `DELETE …/api-keys/{keyId}` |
| **Agents** | `GET/POST …/projects/{projectId}/agents` · `GET/PATCH/DELETE …/agents/{agentId}` · `POST …/agents/{agentId}/archive` · `unarchive` |
| **Agent versions** | `GET/POST …/agents/{agentId}/versions` · `POST …/versions/{versionId}/activate` · `rollback` |
| **Agent credentials** | `GET/POST …/agents/{agentId}/credentials` · `DELETE …/credentials/{credentialId}` |
| **Agent health** | `POST …/agents/{agentId}/health-check` · `GET …/agents/{agentId}/health` · `GET …/health/history` |
| **Datasets** | `GET/POST …/projects/{projectId}/datasets` · `GET/PATCH/DELETE …/datasets/{id}` · `archive`/`unarchive` · `GET/POST …/datasets/{id}/versions` · `…/versions/{vId}/items` · `…/datasets/{id}/stats` |
| **Prompts** | `GET/POST …/prompts` · `GET/PATCH/DELETE …/prompts/{id}` · `GET/POST …/prompts/{id}/versions` · `…/versions/{vId}/activate`·`rollback` · `…/prompts/{id}/compare` |
| **Evaluation profiles** | `GET/POST …/evaluation-profiles` · `GET/PATCH/DELETE …/evaluation-profiles/{id}` |
| **Evaluation jobs** | `GET/POST …/evaluation-jobs` · `GET/DELETE …/evaluation-jobs/{id}` · `POST …/{id}/run`·`cancel` · `GET …/{id}/runs` · `…/runs/{runId}/results` |
| **Benchmarks** | `GET/POST …/benchmarks` · `GET/DELETE …/benchmarks/{id}` · `GET …/{id}/leaderboard` · `POST/DELETE …/{id}/entries` |
| **Regression** | `GET/POST …/regression-checks` · `GET/DELETE …/regression-checks/{id}` |
| **Analytics** | `GET …/analytics?windowDays=30` |
| **Reports** | `POST …/reports/export` · `GET …/reports` · `GET …/reports/{id}` |
| **Search** | `GET …/search?q=…` |
| **Dashboard** | `GET …/dashboard` |
| **Advisor** (P4) | `GET …/advisor` · `GET …/advisor/agents/{agentId}` · `GET …/advisor/prompts/{promptId}?versionId=` |
| **Root cause** (P4) | `GET …/root-cause/jobs/{jobId}` · `GET …/root-cause/regressions/{checkId}` |
| **AI Debugger** (P4) | `GET …/debugger/jobs/{jobId}/runs/{runId}/timeline` |
| **Knowledge graph** (P4) | `GET /api/v1/knowledge/nodes?type=&category=` · `GET …/knowledge/nodes/{nodeKey}` · `GET …/knowledge/graph` |
| System | `GET /api/v1/health` · `GET /actuator/health` |

Authenticate in Swagger with the **bearerAuth** scheme (paste an access token from `/login`).

### Agent Registry data model

| Table | Purpose |
|-------|---------|
| `agents` | Central entity: framework, language, endpoint, auth type, visibility, capabilities, health, active version |
| `agent_versions` | Immutable deployment records; one active per agent; activate/rollback |
| `agent_credentials` | AES-256-GCM-encrypted auth secrets (write-only via API) |
| `agent_health_checks` | Health observation history (status, HTTP code, latency, reason) |
| `agent_tags` | Labels for organising and filtering agents |

### Intelligence Layer data model (Phase 3)

| Table | Purpose |
|-------|---------|
| `datasets` / `dataset_versions` / `dataset_items` | Versioned, immutable evaluation data with import metadata |
| `prompts` / `prompt_versions` | Versioned prompt library with extracted `{{variables}}` |
| `evaluation_profiles` | Reusable metric + threshold rubrics |
| `evaluation_jobs` | Top-level evaluation aggregate (pins agent/dataset/prompt/model versions) |
| `evaluation_runs` / `evaluation_results` | One run per dataset item; one result per metric per run |
| `benchmarks` / `benchmark_entries` | Comparisons of evaluation jobs (leaderboards) |
| `regression_checks` | Candidate-vs-baseline regression findings |
| `reports` | Audit records of generated exports |

### AI Engineering Advisor data model (Phase 4)

| Table | Purpose |
|-------|---------|
| `knowledge_nodes` | Platform-global catalogue of failure modes, regressions, recommendations and optimisations (seeded), with an occurrence counter for future learning |
| `knowledge_edges` | Typed, directed relationships between knowledge nodes (CAUSES / MITIGATED_BY / LEADS_TO / RELATED_TO) |

The advisor, root-cause and AI-debugger features add **no tables**: their recommendations, findings
and timelines are computed **on read** from existing data (consistent with benchmarks/regressions).

---

## Security model

- **Passwords:** BCrypt (work factor 12). **API keys / tokens:** SHA-256 hashes only; raw values
  are returned exactly once.
- **JWT access tokens** are short-lived and stateless; **refresh tokens** are opaque, stored
  server-side and **rotated** on every refresh, so a leaked refresh token has a small blast radius.
- **RBAC:** platform roles (`USER`, `ADMIN`) plus organization roles (`OWNER`/`ADMIN`/`MEMBER`)
  enforced centrally in `OrganizationAccessService` and via method security.
- **CSRF:** intentionally disabled — this is a stateless, token-based API (tokens travel in
  `Authorization` / `X-API-Key` headers that browsers don't attach automatically), so CSRF does
  not apply. Tokens are stored by the SPA and sent explicitly under a strict CSP.
- **Headers:** CSP, `frame-ancestors 'none'`, `X-Content-Type-Options`, Referrer-Policy and HSTS
  on every response. **CORS** is environment-driven and credential-aware.
- **Validation** on every request body; **SQL injection** is prevented by JPA/parameter binding;
  **XSS** is mitigated by React escaping + CSP.
- **Agent credentials** are encrypted at rest with **AES-256-GCM** (never hashed — the platform
  must present them upstream), never returned by the API, never logged. See [ADR 0003](docs/adr/0003-credential-encryption-vs-hashing.md).
- **SSRF protection:** user-supplied agent endpoints are syntactically validated on write and
  re-checked at call time by `OutboundUrlGuard`, which blocks private/loopback/metadata targets
  by default. See [ADR 0004](docs/adr/0004-ssrf-protection-for-agent-endpoints.md).
- **Tenant isolation & IDOR:** every aggregate is resolved by the full
  `(id, projectId, organizationId)` tuple in a dedicated access guard (`AgentAccessGuard`,
  `DatasetAccessGuard`, `PromptAccessGuard`, `EvaluationAccessGuard`, …), so a foreign id resolves to 404.
- **Phase 3 hardening:** evaluation/model calls reuse the same `OutboundUrlGuard` (SSRF); report
  exports neutralise **CSV/formula injection** and **HTML XSS**; analytics, search and dashboard
  queries are always project/organization scoped, so no data crosses tenants.
- **Phase 4 (advisor) isolation:** the advisor, root-cause and AI-debugger features read only through
  the **published services** of other modules, so the same `(id, projectId, organizationId)` tuple
  guards apply — they add no new write or outbound surface. The knowledge graph is **platform-global
  reference data** (no tenant content) requiring only authentication. See [SECURITY_GUIDE §16](docs/SECURITY_GUIDE.md).
- **Observability:** every request carries `X-Correlation-Id` / `X-Request-Id`, propagated to logs
  via MDC and returned in responses (OpenTelemetry-ready; the Phase 4 `TraceRecorder` seam is the
  drop-in point for exporters). See [Observability](#observability) below.

---

## Observability

Brok's Forge ships ready to operate — health probes, metrics, and structured, correlated logs are
on by default.

### Health & Kubernetes-style probes

`GET /actuator/health` reports overall status, with **liveness** and **readiness** probe groups for
orchestrators:

| Endpoint | Group | Meaning |
|----------|-------|---------|
| `GET /actuator/health` | — | Aggregate status (`{"status":"UP"}`); details shown only when authorized |
| `GET /actuator/health/liveness` | `livenessState` | The process is up. A failure means the container should be **restarted**. |
| `GET /actuator/health/readiness` | `readinessState` + `db` | The instance can serve traffic. Because readiness **includes the database**, a DB outage marks the pod **NOT ready** (it is drained from the load balancer) **without** killing it. |

A custom `GET /api/v1/health` complements the Actuator endpoints.

### Metrics — Prometheus / Grafana

`GET /actuator/prometheus` exposes the application's metrics in Prometheus text format —
`http_server_requests` (latency histograms, per route/status), `hikaricp_connections_*` (pool usage),
`jvm_*` (GC pauses, memory, threads), and more. The endpoint is **ADMIN-guarded** by `SecurityConfig`,
so scrapers authenticate (or run on a private management network). Point Prometheus at it and build
Grafana dashboards on the hot paths described in
[`docs/PERFORMANCE_GUIDE.md`](docs/PERFORMANCE_GUIDE.md).

### Structured logs with correlation IDs

- A `CorrelationIdFilter` assigns/propagates a **correlation id** and a per-request **request id**,
  puts both into the SLF4J **MDC**, and returns them as the `X-Correlation-Id` / `X-Request-Id`
  **response headers** — so any log line or client response can be traced end to end.
- In the **`docker` profile**, logs are emitted as **Spring Boot 3.4 native structured JSON** in
  **Elastic Common Schema (ECS)** — one JSON object per line, with the MDC `correlationId` /
  `requestId` as fields — so Loki / ELK / Datadog parse them directly. Override
  `BROKSFORGE_LOG_STRUCTURED_FORMAT=logstash` for a Logstash layout, or set it empty to fall back to
  the human-readable console pattern. The default `dev` profile uses the readable console pattern.
- The stack is **OpenTelemetry-ready**: the correlation id is the natural trace seam, and the Phase 4
  `TraceRecorder` SPI is the single drop-in point for exporters (none wired yet — a deliberate scope
  boundary).

---

## Screenshots

> 📸 The images below are **placeholders** for the V1 release. They are captured and standardized per
> [`docs/SCREENSHOT_GUIDE.md`](docs/SCREENSHOT_GUIDE.md) (authored separately) and dropped into
> `docs/assets/`. No image files are committed yet — replace these links when the assets land.

| | |
|---|---|
| ![Dashboard](docs/assets/dashboard.png) | **Project dashboard** — the operational and quality roll-up across agents, evaluations and analytics. |
| ![Agents](docs/assets/agents.png) | **Agent registry** — search, filter and paginate framework-agnostic agents. |
| ![Agent detail](docs/assets/agent-detail.png) | **Agent detail** — Overview / Versions / Health / Credentials / Settings tabs. |
| ![Evaluations](docs/assets/evaluations.png) | **Evaluation job** — summary, runs and per-run metric results. |
| ![Benchmarks](docs/assets/benchmarks.png) | **Benchmark leaderboard** — compare agents / versions / prompts / models. |
| ![Advisor](docs/assets/advisor.png) | **AI Engineering Advisor** — ranked recommendations with why / what changed / how to fix / expected improvement / confidence / severity. |
| ![AI Debugger](docs/assets/debugger.png) | **AI Debugger** — the stage-by-stage execution timeline for a single run. |
| ![Knowledge graph](docs/assets/knowledge.png) | **Knowledge graph** — the seeded failure-mode / recommendation catalogue and a node's neighbours. |

---

## Verifying the build

After `docker compose up --build`, confirm each capability:

1. **Health** — `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.
2. **Register** — open http://localhost:3000/register, create an account → lands on the dashboard.
3. **Email verification** — check the **backend logs** (`docker compose logs backend`) for the
   verification email and its link (the dev email transport logs messages instead of sending).
4. **Organizations** — create one; you become its `OWNER`.
5. **Members** — add another registered user by email; change their role; remove them.
6. **Projects** — create a project inside the organization.
7. **API keys** — open the project, create a key, copy it once. Revoke it.
8. **Agents** — open **Agents** (sidebar) or a project's *Agents* tab → *Register agent*; search
   and filter the list.
9. **Versions** — on an agent, *Versions* → register a version with *Activate immediately*; then
   register another and *Rollback*.
10. **Credentials** — *Credentials* tab → set an API-key credential (admin only); confirm the
    secret is never shown again, only a masked hint.
11. **Health** — *Health* tab → *Run check* (point an agent at any reachable URL; in dev,
    `AGENT_HEALTH_ALLOW_PRIVATE_TARGETS=true` allows localhost) → see status, latency, availability.
12. **Password reset** — use *Forgot password*; grab the reset link from the backend logs.
13. **Swagger** — explore http://localhost:8080/swagger-ui.html.
14. **Datasets** — open **Datasets** → create one → import a CSV/JSON version → view items and stats.
15. **Prompts** — create a prompt → add a version with `{{variables}}` → activate, then add another and *Compare*/*Rollback*.
16. **Evaluation profile** — create a profile (e.g. `EXACT_MATCH` + `LATENCY` with a threshold).
17. **Evaluation job** — create a job (agent + dataset + optional prompt/profile) with *Run immediately*; it invokes the agent endpoint per row and scores metrics (in dev, `MODEL_ALLOW_PRIVATE_TARGETS=true` allows localhost agents). Inspect the summary, runs and per-run results.
18. **Benchmark / Regression** — add two completed jobs to a benchmark and view the leaderboard; run a regression check between a baseline and candidate job.
19. **Analytics / Dashboard / Reports** — view analytics trends, the project dashboard, and export an evaluation job report (JSON/CSV/HTML).
20. **Advisor** — open **Advisor** (sidebar) for the project advisory; open an agent and a prompt detail page → *Advisor* for agent- and prompt-scoped recommendations (each with why / what changed / how to fix / expected improvement / confidence / severity).
21. **Root cause & AI Debugger** — on an evaluation job → *Root cause* for the diagnosed failures; on a run → *Debug* to see the stage-by-stage execution timeline.
22. **Knowledge graph** — open **Knowledge** (sidebar) to browse the seeded failure-mode / recommendation catalogue and a node's graph neighbours.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| **API exits immediately on startup** with a message about a missing JWT/encryption secret | `JWT_SECRET` or `ENCRYPTION_KEY` is unset — the app **fails fast** by design | Set both in `.env` (Base64). Generate with `openssl rand -base64 48` (JWT) and `openssl rand -base64 32` (encryption; must decode to **exactly 32 bytes**). |
| **`APPLICATION FAILED TO START` — *"Parameter N of constructor in AuthService required a bean of type ...EmailService that could not be found"*** | You are running a **stale backend image** built before the current source. `docker compose up -d` reuses the existing image; `docker compose down -v` does **not** rebuild it | Rebuild the image from current source: `docker compose up -d --build backend` (or `--build` for the whole stack). Confirm with `docker compose logs backend \| grep "Started BroksForgeApplication"`. |
| **`Bind for 0.0.0.0:8080 failed: port is already allocated`** (or 3000 / 5432 / 6379) | A host port is taken by another process or a previous stack | Stop the other process, or change the host port in `.env` (`BACKEND_PORT`, `FRONTEND_PORT`, `POSTGRES_PORT`, `REDIS_PORT`). |
| **API won't boot after a schema change**: *Flyway validation failed / migration checksum mismatch* | A Flyway migration was edited, or a **dirty volume** holds an older/partial schema | Migrations are **append-only** — never edit an applied one. For a local reset, wipe the volume: `docker compose down -v` then `docker compose up --build` (this **deletes all data**). |
| **Agent health check or evaluation against `localhost` is rejected** | The **SSRF guard** (`OutboundUrlGuard`) blocks private / loopback / metadata targets by default | For local testing only, set `AGENT_HEALTH_ALLOW_PRIVATE_TARGETS=true` and/or `MODEL_ALLOW_PRIVATE_TARGETS=true` in `.env`. **Keep both `false` in production.** |
| **Frontend loads but every API call fails** (CORS error, network error, or 401 loops) | The browser can't reach the API, or its origin isn't allowed | Check `NEXT_PUBLIC_API_BASE_URL` (the URL the **browser** uses to reach the API — it must be reachable from your machine, not the Docker network) and ensure that origin is in `CORS_ALLOWED_ORIGINS`. |
| **`db` readiness is DOWN / `/actuator/health/readiness` returns 503** | Postgres isn't up yet or is unreachable | Wait for the Postgres health check, or inspect `docker compose logs postgres`. Readiness **includes the DB** on purpose. |
| **Emails (verify / reset) never arrive** | The dev mail transport **logs** messages instead of sending them | Read the link from `docker compose logs backend` (see [Verifying the build](#verifying-the-build)). |

If a request fails, capture its `X-Correlation-Id` response header and grep the backend logs for that
id — every log line carries it (see [Observability](#observability)).

---

## FAQ

**Is it production-ready?**
The platform is built to production standards — fail-fast config, multi-tenant isolation, encrypted
credentials, SSRF defence, append-only migrations, health/metrics/structured logging, and a test
strategy with a per-module security gate. Version **1.0.0** delivers Phases 1–4 end to end. Some
hardening items are explicitly **future** work and labelled as such (async evaluation workers, a Redis
read cache, rate limiting, an egress proxy) — see [`docs/PERFORMANCE_GUIDE.md`](docs/PERFORMANCE_GUIDE.md)
and [`docs/ROADMAP.md`](docs/ROADMAP.md). Run your own security and load review before exposing it to
untrusted traffic.

**What models / providers are supported?**
Brok's Forge is **provider-agnostic**. A `ModelInvoker` SPI abstracts invocation, with named providers
for **OpenAI, Anthropic, Groq, Ollama, Gemini, OpenRouter and DeepSeek**, and a key-free
**agent-endpoint** invoker (`AgentEndpointInvoker`) as the default execution target — so the platform
evaluates *your* deployed agent over HTTP regardless of the framework behind it (Spring AI, LangGraph,
CrewAI, AutoGen, PydanticAI, Semantic Kernel, custom REST, …).

**Is it multi-tenant?**
Yes. **Organizations** are the tenant boundary; **projects** nest under them and most resources are
project-scoped. Every aggregate is resolved by the full `(id, projectId, organizationId)` tuple in a
dedicated access guard, so a foreign id resolves to **404** (no existence leak). Roles are platform
(`USER` / `ADMIN`) plus organization (`OWNER` / `ADMIN` / `MEMBER`).

**How does it stay provider-agnostic?**
Two mechanisms: anything provider-specific lives **behind the `ModelInvoker` interface**, and
enumerations (framework, provider, metric type, …) are **stored as text**, so adding a provider or
framework is a **code-only change — never a database migration**.

**Where are the advisor's recommendations stored?**
They aren't. Advisory reports, root-cause findings and AI-debugger timelines are **computed on read**
from existing platform data each time they're requested, so they can never drift — exactly like
benchmark leaderboards and regression findings. The advisor adds **no tables**. The only persisted
Phase 4 data is the **Engineering Knowledge Graph** (two small, platform-global reference tables,
`knowledge_nodes` / `knowledge_edges`, seeded via Flyway).

**Why are there no async workers yet?**
The evaluation executor runs **synchronously** today behind a deliberate **queue-ready seam**
(`EvaluationExecutor`). Starting and executing a job are already separated behind an interface, so
moving execution onto async workers / a queue is a localised change that doesn't touch callers or the
`Job → Run → Result` schema. The trade-offs (and the resource-holding issue it fixes) are documented
in [`docs/PERFORMANCE_GUIDE.md`](docs/PERFORMANCE_GUIDE.md); it's scheduled on the
[roadmap](docs/ROADMAP.md).

**Is there an SDK or CLI?**
Not yet. A typed **SDK** (TypeScript, then Python) and a CI-friendly **`broksforge` CLI** are planned
(see [Roadmap](#roadmap)). Today the full surface is a documented REST API explorable at
**Swagger UI** (`/swagger-ui.html`), with a stable OpenAPI contract.

---

## Roadmap

Phases 1–4 are delivered (Foundation → Agent Registry → Intelligence Layer → AI Engineering Advisor). See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the full history and forward plan. Next up:

- 🔭 **Live tracing & RAG / memory inspectors** — drive the `TraceRecorder` seam with real spans, lighting up the AI Debugger's uninstrumented stages and feeding the RAG advisor real retrieval data
- 🧰 **SDK & CLI** — a typed client and a CI-friendly command-line tool
- ⚙️ **Async evaluation workers** — move job execution off the request thread (queue-ready seam already in place)

---

<div align="center">
<sub>Built with care as a production SaaS and an open-source project.</sub>
</div>
