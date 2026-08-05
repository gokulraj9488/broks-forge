# Developer Setup & Build

## Prerequisites

| For | You need |
| --- | --- |
| Running the stack | Docker Desktop with Compose v2 |
| Backend development | JDK 21, Maven 3.9+ |
| Frontend development | Node.js 20+, npm |

## Full stack in Docker

```bash
git clone https://github.com/gokulraj9488/broks-forge.git
cd broks-forge
cp .env.example .env

# Required — the API fails fast without them
openssl rand -base64 48   # → JWT_SECRET
openssl rand -base64 32   # → ENCRYPTION_KEY (must be 32 bytes)

docker compose up --build
```

| Service | URL |
| --- | --- |
| Web app | `http://localhost:3000` |
| API | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Health | `http://localhost:8080/actuator/health` |

`docker compose down` to stop; add `-v` to wipe volumes.

## Local development

Run only the datastores in Docker and the app tiers natively — much faster iteration.

```bash
docker compose up postgres redis
```

**Backend:**

```bash
cd backend
mvn spring-boot:run
```

Flyway applies migrations at startup.

**Frontend:**

```bash
cd frontend
npm install
npm run dev
```

## The commands that gate a change

Run all four before proposing anything.

```bash
# Backend — compiles, then the full suite against real PostgreSQL
cd backend
mvn -B clean test

# Frontend
cd frontend
npx tsc --noEmit      # types
npx next lint         # lint
npm run build         # production build
```

The backend suite is currently **499 tests**. Integration tests use **Testcontainers** against a
real PostgreSQL 16 rather than an in-memory database, so migrations, constraints and SQL behaviour
are exercised exactly as they will run.

## Running a single test

```bash
cd backend
mvn -o -B test -Dtest=BrokIntegrationTest
mvn -o -B test -Dtest=InvestigationIntegrationTest
```

Use `-o` (offline) once dependencies are cached — it is noticeably faster.

## Known environment issues

**Testcontainers startup on constrained machines.** If Docker has limited memory, container startup
can exceed the default wait. The suite sets an explicit five-minute startup timeout for this reason.
If you still see `ContainerLaunchException: Timed out waiting for log output`, give Docker more
memory and stop the compose stack before running tests.

**Java language servers and `target/`.** An IDE Java language server that rebuilds into
`backend/target/classes` while Maven is running can produce spurious `ClassNotFoundException`s
across the whole suite — on classes that plainly exist. If you see mass failures on untouched code,
this is almost certainly the cause. Close the IDE or suspend the language server and re-run before
investigating further.

**Do not run the frontend build and the backend suite concurrently** on a machine with limited
cores; they will starve each other and the database container.

## Environment variables

| Variable | Required | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | ✅ | Signs access and refresh tokens |
| `ENCRYPTION_KEY` | ✅ | Encrypts provider credentials (32 bytes) |
| `SPRING_DATASOURCE_URL` | — | Defaults to the compose Postgres |
| `SPRING_DATA_REDIS_HOST` | — | Redis; auth rate limiting degrades gracefully without it |
| `BROKSFORGE_PLATFORM_V2_ENABLED` | — | Defaults to `true`; gates Registry, Graph, Intelligence, Brok, Root Cause Explorer |
| `NEXT_PUBLIC_API_URL` | — | API base URL for the frontend |

## Project layout

```
   backend/           Spring Boot API
     src/main/java/com/broksforge/
       modules/       one package per module
       config/  security/  common/
     src/main/resources/db/migration/    Flyway
     src/test/java/                      integration + unit tests
   frontend/          Next.js app
     src/app/  src/components/  src/lib/
     content/docs/    the public documentation
   docs/              the engineering handbook
   docker-compose.yml
```

## Migrations

Flyway, versioned, forward-only.

- Never modify an applied migration — add a new one.
- Name them `V<n>__snake_case_description.sql`.
- Every new table needs `organization_id`, and `project_id` where it applies.

See also: [Module Structure](/docs/module-structure) · [Contributing](/docs/contributing) ·
[Deployment](/docs/deployment) · [Testing Strategy](/docs/testing-strategy)
