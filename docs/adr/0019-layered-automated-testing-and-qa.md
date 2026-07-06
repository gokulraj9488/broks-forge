# 19. Layered automated testing strategy and quality assurance

- Status: Accepted
- Date: 2026-07-07

## Context

Through v1.0 the platform was verified by hand and by CI build steps (`mvn verify`, `next build`),
but there was **no automated test suite** in the repository and no documented QA process. Regressions
were only caught by manual re-testing — for example the agent-registration incidents (the V30
soft-delete/unique-constraint mismatch and the provider-aware health-check URL) were found in
production use, not by tests. As the platform grows across many modules (agents, credentials,
evaluations, benchmarks, knowledge, advisor) this does not scale and risks silent regressions.

We need a testing strategy that is fast enough to run on every change, high-confidence enough to trust
before a release, faithful to production (real PostgreSQL, real Flyway, real security), and **purely
additive** — it must not alter production behaviour or the modular-monolith architecture.

## Decision

Adopt a **layered (pyramid) testing strategy**, all additive:

1. **Unit tests** (JUnit 5 + Mockito + AssertJ) for pure logic — slug generation, secure tokens,
   AES-256-GCM encryption, JWT, the SSRF guard, the provider-aware health-probe planner, e-mail
   content, and domain behaviour. No Spring context; millisecond-fast.
2. **Integration tests** (`@SpringBootTest` + **Testcontainers PostgreSQL 16** + MockMvc) for wiring,
   schema/`ddl-validate`, Flyway, security (deny-by-default), auth, and the agent-registration
   lifecycle. A **singleton container** is shared across the run for speed. Test-only secrets and a
   `test` profile live in `application-test.yml`.
3. **API tests** — a Postman/Newman collection covering success and negative cases (401/400/404/409)
   and secret confidentiality, chaining real state.
4. **End-to-end tests** — a **self-contained Playwright project in `e2e/`**, deliberately outside
   `frontend/` so it never affects the app's dependency graph or `npm ci`.
5. **Manual QA** — a living `docs/QA_CHECKLIST.md` (100+ cases) and a release-readiness report.

CI stays split by concern: `backend-ci.yml` runs JUnit + Testcontainers + Flyway + JaCoCo;
`frontend-ci.yml` runs lint/type-check/build; a new `e2e.yml` boots the compose stack and runs
Playwright + Newman. Coverage is **measured and published** (JaCoCo) with documented targets, but the
hard gate is deferred until the young suite clears the thresholds, so it never blocks a legitimate PR.

## Consequences

- Regressions in security, schema, encryption, provider probing and agent registration are now caught
  automatically; the two production incidents above have dedicated regression tests.
- Testcontainers requires a Docker daemon for the integration layer (present locally and on CI
  runners); pure unit tests need nothing.
- Test dependencies (Testcontainers, JaCoCo, Playwright, Newman) are **test/dev-scoped or isolated**;
  production artifacts and runtime behaviour are unchanged.
- The suite is a starting point, not a finish line — coverage grows per module (see
  `docs/AUTOMATED_TESTING.md`), and a JaCoCo gate + backend formatter are planned follow-ups.
