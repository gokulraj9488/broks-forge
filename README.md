<div align="center">

# ⚒️ Brok's Forge

### The Engineering Platform for AI Agents.

A production-grade, open-source foundation for building, shipping and operating AI agents.
Modular monolith · Clean Architecture · Built to scale into microservices.

</div>

---

> **Status — Milestone 1: Foundation.**
> This milestone delivers the complete platform foundation: authentication, organizations,
> members, projects, API keys, security, persistence and a polished web app. AI evaluation,
> prompt versioning, tracing and benchmarking are **future modules** that plug into this base.

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Folder structure](#folder-structure)
- [Quick start (Docker)](#quick-start-docker)
- [Local development](#local-development)
- [Environment variables](#environment-variables)
- [API & Swagger](#api--swagger)
- [Security model](#security-model)
- [Verifying the build](#verifying-the-build)
- [Future modules](#future-modules)

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

---

## Folder structure

```
broks-forge/
├── docker-compose.yml          # postgres · redis · backend · frontend
├── .env.example                # copy to .env
├── docs/adr/                   # Architecture Decision Records (0001–0004)
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
│       │       └── agent/      # Phase 2: domain · repository · service · web
│       │                       #   (Agent, AgentVersion, AgentCredential, AgentHealthCheck, AgentTag)
│       └── resources/
│           ├── application*.yml
│           └── db/migration/   # Flyway V1…V10  (V6–V10 = agent registry)
│
└── frontend/                   # Next.js 15 / React 19 / TS
    ├── Dockerfile
    ├── package.json
    └── src/
        ├── app/                # App Router: (auth), (dashboard incl. agents), verify-email
        ├── components/         # ui/ (shadcn-style), layout/, agents/, feature dialogs & panels
        └── lib/                # api client, hooks (TanStack Query), store (Zustand), zod
```

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
- **Tenant isolation & IDOR:** agents are always resolved by the full
  `(id, projectId, organizationId)` tuple in `AgentAccessGuard`, so a foreign id resolves to 404.
- **Observability:** every request carries `X-Correlation-Id` / `X-Request-Id`, propagated to logs
  via MDC and returned in responses (OpenTelemetry-ready).

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

---

## Future modules

This foundation is built so the next modules plug in cleanly:

- 🧪 **Evaluations** — score agent outputs against datasets & rubrics
- 📝 **Prompt versioning** — version, diff and roll back prompts
- 🔭 **Tracing** — capture and inspect agent runs end-to-end
- 📊 **Benchmarking** — compare models, prompts and configurations

---

<div align="center">
<sub>Built with care as a production SaaS and an open-source project.</sub>
</div>
