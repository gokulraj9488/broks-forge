# Brok's Forge — Automated Testing

How Brok's Forge is tested automatically: the architecture, what exists today, coverage targets, and
exactly how to run every layer locally and in CI.

## Testing philosophy — the pyramid

```
        ▲  fewer, slower, higher-confidence
        │      E2E (Playwright)  ── real browser, real stack
        │      API (Postman/Newman) ── contract + negative cases
        │      Integration (@SpringBootTest + Testcontainers) ── real Postgres, Flyway, security
        │      Slice / repository (real schema)
        │      Unit (JUnit 5, Mockito, pure functions)
        ▼  many, fast, isolated
```

Fast unit tests catch logic regressions in milliseconds; integration tests catch wiring, schema and
security regressions against a **real PostgreSQL 16** (the same image production uses); E2E tests catch
UX regressions in a real browser. Nothing is mocked that can be exercised for real cheaply.

---

## 1. Current coverage (implemented)

| Layer | Tool | Location | Count |
|-------|------|----------|-------|
| Unit | JUnit 5 + AssertJ + Mockito | `backend/src/test/java/**` | 37 |
| Integration | Spring Boot Test + Testcontainers + MockMvc | `backend/src/test/java/**` | 23 |
| **Backend total** | | | **60 (all green)** |
| E2E | Playwright | `e2e/tests/**` | 32 tests / 12 specs |
| API | Postman / Newman | `docs/api-testing/**` | ~22 requests w/ assertions |

Backend suite verified green: `Tests run: 60, Failures: 0, Errors: 0`.

### What the backend suite proves

- **Encryption** (`CredentialEncryptionServiceTest`): AES-256-GCM round-trip, versioned non-deterministic ciphertext, tamper rejection, key-size validation.
- **JWT** (`JwtServiceTest`): issue/parse round-trip, foreign-signature rejection, expiry, weak-secret rejection.
- **Tokens** (`SecureTokensTest`): entropy, zero-padded OTP codes, SHA-256 determinism, constant-time compare.
- **SSRF guard** (`OutboundUrlGuardTest`): public allowed; loopback/private/metadata/scheme/embedded-credentials blocked; opt-in for private targets.
- **Provider-aware probing** (`HealthProbePlannerTest`): Groq/OpenAI/OpenRouter/Ollama/Anthropic/Gemini → `GET …/models`; Spring AI → `/actuator/health`; frameworks → `/health`; generic → `GET_ROOT`.
- **Slug generation, email content (HTML-escaping), agent domain (soft-delete/archive)**.
- **Flyway** (`FlywayMigrationIntegrationTest`): all migrations applied; the V30 partial unique index is present (`WHERE deleted = false`).
- **Auth** (`AuthControllerIntegrationTest`): register/login/duplicate/validation/guard.
- **Security** (`SecurityIntegrationTest`): deny-by-default across every module; public health.
- **Agent registration** (`AgentRegistrationIntegrationTest`): `credentialConfigured` lifecycle, **slug reuse after soft-delete (V30)**, live-duplicate auto-suffix, credential secret never returned.
- **Health probes** (`HealthEndpointIntegrationTest`): liveness/readiness UP.
- **Context/Flyway/`ddl-validate`** boot smoke test.

---

## 2. Backend tests

**Stack:** JUnit 5, Spring Boot Test, Mockito, AssertJ, MockMvc, Testcontainers (PostgreSQL 16).

**Layout**

```
backend/src/test/
├── resources/application-test.yml          # test profile (test keys, rate-limit off)
└── java/com/broksforge/
    ├── support/AbstractIntegrationTest.java # singleton Testcontainers Postgres + MockMvc + helpers
    ├── ApplicationContextIntegrationTest.java
    ├── actuator/HealthEndpointIntegrationTest.java
    ├── migration/FlywayMigrationIntegrationTest.java
    ├── security/{JwtServiceTest, SecurityIntegrationTest}.java
    ├── common/{util,security}/*Test.java    # slug, tokens, encryption, SSRF
    └── modules/{agent,auth,user}/**/*Test.java
```

**Run**

```bash
cd backend
mvn test                     # unit + integration (needs Docker for Testcontainers)
mvn verify                   # + JaCoCo coverage report at target/site/jacoco/index.html
mvn -Dtest=HealthProbePlannerTest test   # a single class
```

Integration tests use a **singleton container** (one Postgres for the whole run) started in a static
initializer, so the suite is fast. Docker must be available (it is on GitHub `ubuntu-latest`).

**Conventions**

- Pure logic → plain unit tests (no Spring). Wiring/schema/security → `AbstractIntegrationTest`.
- Never assert on secrets; assert they are **absent**.
- Prefer real behaviour over mocks; mock only true external boundaries.
- One behaviour per test; `@DisplayName` reads like a sentence.

---

## 3. Frontend / E2E tests (Playwright)

Self-contained project in **`e2e/`** with its own `package.json` — deliberately **outside** `frontend/`
so it never affects the Next.js app build or `npm ci`.

**Run**

```bash
docker compose up --build       # stack must be running (see repo root)
cd e2e
npm ci
npm run install:browsers        # one-time Chromium download
npm test                        # or: npm run test:ui
```

Covers: register, login, logout, wrong credentials, forgot/reset password, OTP wizard, session-expired
banner, org/project/agent/dataset/prompt creation, "setup required" gating, evaluations/benchmarks
pages, theme, sidebar navigation, 404, SEO metadata (title/OG/robots/sitemap/manifest), and responsive
layout. See `e2e/README.md`.

---

## 4. API tests (Postman / Newman)

`docs/api-testing/` holds a Postman v2.1 collection + environment. It chains state (token → org →
project → agent → credential) and asserts success + negative cases (401/400/404/409) and secret
confidentiality. Run in Postman's Runner or headless:

```bash
newman run docs/api-testing/broks-forge.postman_collection.json \
  -e docs/api-testing/broks-forge.postman_environment.json
```

---

## 5. CI/CD

| Workflow | Trigger | Does |
|----------|---------|------|
| `.github/workflows/backend-ci.yml` | PR/push touching `backend/**` | `mvn clean verify` → **JUnit + Testcontainers + Flyway + JaCoCo**; uploads JAR + coverage |
| `.github/workflows/frontend-ci.yml` | PR/push touching `frontend/**` | `npm ci` → **lint + `tsc --noEmit` + build** |
| `.github/workflows/docker.yml` | PR/push | Builds the Docker images |
| `.github/workflows/e2e.yml` | PR/push | **`docker compose up --build`** → waits for health → **Playwright** + **Newman**; uploads reports |
| `.github/workflows/codeql.yml` | PR/push/schedule | Static security analysis |
| `.github/workflows/dependency-review.yml` | PR | Flags vulnerable dependency changes |

Any failing job fails the check. Coverage is published as an artifact (`jacoco-coverage-report`).
`.github/workflows/release.yml` handles tagged releases.

**Formatting note:** frontend formatting/lint is enforced via ESLint (`next lint`). A backend
formatter (Spotless/Checkstyle) is **not yet wired** — see "Gaps" below.

---

## 6. Coverage goals (targets)

Coverage is **measured** by JaCoCo today and **published** per build; a hard gate is intentionally
not enforced yet while the suite grows (so it never blocks a legitimate PR). Targets to grow into:

| Area | Line coverage target |
|------|----------------------|
| Controllers (`**/web/**`) | 90% |
| Services (`**/service/**`) | 90% |
| Repositories | 85% |
| Utilities / security (`common/**`, `security/**`) | 95% |
| Domain (`**/domain/**`) | 80% |
| **Overall** | **≥ 85%** |

To enforce later, add a JaCoCo `check` execution to `backend/pom.xml`:

```xml
<execution>
  <id>jacoco-check</id>
  <phase>verify</phase>
  <goals><goal>check</goal></goals>
  <configuration>
    <rules>
      <rule>
        <element>BUNDLE</element>
        <limits>
          <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.85</minimum></limit>
        </limits>
      </rule>
    </rules>
  </configuration>
</execution>
```

---

## 7. Run everything (quick reference)

```bash
# Backend unit + integration (+ coverage)
cd backend && mvn verify

# Frontend static checks
cd frontend && npm ci && npm run lint && npm run typecheck && npm run build

# Full stack
docker compose up --build

# E2E (stack must be up)
cd e2e && npm ci && npm run install:browsers && npm test

# API (stack must be up)
newman run docs/api-testing/broks-forge.postman_collection.json \
  -e docs/api-testing/broks-forge.postman_environment.json
```

---

## 8. Gaps & next increments

- **Grow backend coverage** toward the targets above — service-level tests for evaluations, benchmarks,
  datasets, prompts and the advisor/knowledge engines (patterns established in the current suite).
- **Backend formatter** (Spotless + `palantir-java-format` or Checkstyle) wired into `mvn verify` and CI.
- **Enforce the JaCoCo gate** once coverage clears the thresholds.
- **Contract tests** for the OpenAPI schema (e.g. schemathesis) to fuzz the API against `/v3/api-docs`.
- **Playwright**: add `data-testid`s to primary actions and expand deep CRUD assertions; run cross-browser (Firefox/WebKit) in CI nightly.
- **Load/performance** smoke (k6/Gatling) for the evaluation and listing endpoints.
- **Accessibility automation** (`@axe-core/playwright`) folded into the E2E run.
