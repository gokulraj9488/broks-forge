# Contributing to Brok's Forge

Thanks for your interest in **Brok's Forge — The Engineering Platform for AI Agents**.
This page is the quick, GitHub-facing entry point. The full, authoritative contributor
guide lives in **[docs/CONTRIBUTING.md](./docs/CONTRIBUTING.md)** — read it before opening
a non-trivial pull request.

By participating you agree to our [Code of Conduct](./CODE_OF_CONDUCT.md).

---

## Prerequisites

| Tool                       | Version       | Purpose                                            |
| -------------------------- | ------------- | -------------------------------------------------- |
| **JDK**                    | **21** (LTS)  | Backend — Spring Boot 3.4.1, Maven (`backend/`).   |
| **Node.js**                | **20+**       | Frontend — Next.js 15 / React 19 (`frontend/`).    |
| **Docker** + Compose       | current       | PostgreSQL 16, Redis 7, and the one-command stack. |
| `openssl`                  | any           | Generating local secrets.                          |
| `git`                      | any           | Version control and Conventional Commits.          |

> There is **no Maven wrapper** — use a locally installed Maven (`mvn`).
> On Windows, use Git Bash or WSL for the `openssl` commands.

---

## Clone & run

```bash
git clone https://github.com/your-org/broks-forge.git
cd broks-forge
```

### Required secrets

Two Base64 secrets are read **only** from the environment (never committed). Generate fresh
values — they **must differ**:

```bash
# JWT signing secret (HS256, >= 32 bytes)
openssl rand -base64 48

# AES-256-GCM credential-encryption key (exactly 32 bytes)
openssl rand -base64 32
```

Copy the example env file and paste your values in:

```bash
cp .env.example .env
# then set JWT_SECRET and ENCRYPTION_KEY in .env
```

> Losing `ENCRYPTION_KEY` makes stored agent credentials unrecoverable. Keep it safe and
> out of source control.

### Run the full stack (Docker Compose)

```bash
docker compose up --build
```

This starts **PostgreSQL**, **Redis**, the **backend** (Flyway applies migrations `V1..Vn`
on startup; Hibernate runs `ddl-auto=validate`), and the **frontend**.

- Frontend: <http://localhost:3000>
- Backend API: <http://localhost:8080> (OpenAPI/Swagger UI + `/actuator/health`)

### Run locally (without containers)

```bash
# Backend
mvn -B -ntp -f backend/pom.xml clean verify   # compile + tests (needs Docker for Testcontainers)

# Frontend
cd frontend
npm ci
npm run dev
```

---

## Branch & commit conventions

Branch off the default branch with a typed, descriptive name:

```text
feat/evaluation-job-executor
fix/dataset-csv-import-encoding
docs/roadmap-phase4
```

Use **[Conventional Commits](https://www.conventionalcommits.org/)** — `type(scope): summary`:

```text
feat(evaluation): add EvaluationJob -> Run -> Result pipeline
fix(agent): scope health-check load by (id, projectId, organizationId)
docs(adr): add ADR 0015 for async eval workers
```

Common types: `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `chore`, `build`, `ci`.
Keep the summary imperative and under ~72 characters.

---

## Before you open a PR — checklist

The full Definition of Done is in [docs/CONTRIBUTING.md](./docs/CONTRIBUTING.md). At minimum:

- [ ] **Tests added/updated** and passing
      (`mvn -B -ntp -f backend/pom.xml verify`; `npm run lint && npx tsc --noEmit && npm run build`).
- [ ] **Migrations are append-only** — no released `V1..Vn` touched; the app boots under `ddl-auto=validate`.
- [ ] **Security** — RBAC enforced; loads scoped by `(id, projectId, organizationId)`; outbound URLs guarded;
      no secret stored in plaintext, logged, or returned (see [Security Guide §14](./docs/SECURITY_GUIDE.md#14-per-endpoint-security-review-checklist)).
- [ ] **Docs/ADRs updated** and cross-linked; no existing feature removed or weakened.

CI runs backend, frontend, CodeQL, Docker build, and dependency-review checks on every PR.

---

## Further reading

- **[docs/CONTRIBUTING.md](./docs/CONTRIBUTING.md)** — the full contributor guide (Definition of Done, module layout).
- **[docs/ENGINEERING_HANDBOOK.md](./docs/ENGINEERING_HANDBOOK.md)** — how the platform is engineered and operated.
- **[docs/CODING_STANDARDS.md](./docs/CODING_STANDARDS.md)** — detailed code-style conventions.
- **[docs/TESTING_STRATEGY.md](./docs/TESTING_STRATEGY.md)** — the testing pyramid and "must test" gates.
- **[docs/SECURITY_GUIDE.md](./docs/SECURITY_GUIDE.md)** — threat model and per-endpoint security controls.

Questions? Open a [Discussion](https://github.com/your-org/broks-forge/discussions) or see
[SUPPORT.md](./SUPPORT.md). Found a vulnerability? Follow the [Security Policy](./SECURITY.md).
