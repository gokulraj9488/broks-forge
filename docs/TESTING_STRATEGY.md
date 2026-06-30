# Testing Strategy — Brok's Forge

- Audience: backend & frontend engineers, reviewers, CI maintainers.
- Stack under test: Java 21 / Spring Boot 3.4.1, PostgreSQL + Flyway (`ddl-auto=validate`), Redis, Next.js 15.
- Test stack: JUnit 5 (Jupiter) + AssertJ + Mockito + Spring Boot Test (`@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`) + `spring-security-test` + **Testcontainers (PostgreSQL)**.
- Status: living document. Last reviewed 2026-07-01 (Phase 5).

---

## 1. The testing pyramid for this stack

```
                ▲  fewer, slower, highest confidence
                │
        ┌───────────────┐
        │  E2E / system │   @SpringBootTest + Testcontainers (full request → DB)
        ├───────────────┤   Frontend: Playwright (critical flows)
        │ Slice tests   │   @DataJpaTest (repos/specs, Testcontainers PG)
        │ (integration- │   @WebMvcTest (controllers + security)
        │  lite)        │   Contract / OpenAPI
        ├───────────────┤
        │  Unit tests   │   services, metric evaluators, OutboundUrlGuard,
        │  (the base)   │   CredentialEncryptionService, SlugGenerator, var extraction
        └───────────────┘
                │
                ▼  many, fast, run on every commit
```

- **Most** tests are **unit** tests — fast, no Spring context, pure logic.
- **Slice** tests load only the relevant Spring layer (JPA or MVC). `@DataJpaTest` uses **real PostgreSQL via Testcontainers** because the schema is owned by **Flyway** and `ddl-auto=validate` will fail against an in-memory H2 with a different dialect/DDL.
- **Integration/system** tests are the **fewest** — they exercise the full `controller → service → repository → DB` path and are reserved for high-value flows.

---

## 2. Unit tests (the base)

Pure JUnit 5 + AssertJ + Mockito. No Spring context. Collaborators are mocked; the **unit under test is real**.

### 2.1 What to unit-test

| Component | Focus |
|-----------|-------|
| **Services** | business rules, RBAC delegation, mapping, edge cases |
| **Metric evaluators** (evaluation) | scoring math, boundary values, null/empty handling |
| **`OutboundUrlGuard`** | blocks loopback/link-local/private/unique-local/metadata; allows public; honors `allow-private-targets` |
| **`CredentialEncryptionService`** | **encrypt→decrypt round-trip**; versioned ciphertext shape; tamper → decryption failure |
| **`SlugGenerator`** | slugification, collision/uniqueness suffixing, length bounds |
| **Variable extraction** (prompt `{{var}}`) | extracts names, ignores malformed, treats content as data |

### 2.2 Example — `CredentialEncryptionService` round-trip & tamper

```java
class CredentialEncryptionServiceTest {

    // 32-byte Base64 key, test-only; never a real key.
    private static final String TEST_KEY = Base64.getEncoder()
        .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private final CredentialEncryptionService service =
        new CredentialEncryptionService(TEST_KEY, /* keyVersion */ 1);

    @Test
    void encryptThenDecrypt_roundTrips() {
        String plaintext = "super-secret-agent-token";

        String ciphertext = service.encrypt(plaintext);

        assertThat(ciphertext).startsWith("v1:");                 // self-describing, versioned
        assertThat(ciphertext.split(":")).hasSize(3);             // v<ver>:<iv>:<ct+tag>
        assertThat(ciphertext).doesNotContain(plaintext);         // not plaintext on disk
        assertThat(service.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void distinctIvs_produceDifferentCiphertexts() {
        assertThat(service.encrypt("same")).isNotEqualTo(service.encrypt("same"));
    }

    @Test
    void tamperedCiphertext_failsAuthTag() {
        String ct = service.encrypt("payload");
        String tampered = ct.substring(0, ct.length() - 2) + "AA"; // flip the GCM tag region

        assertThatThrownBy(() -> service.decrypt(tampered))
            .isInstanceOf(CredentialDecryptionException.class);     // GCM integrity check fails
    }
}
```

### 2.3 Example — `OutboundUrlGuard` SSRF rules

```java
class OutboundUrlGuardTest {

    private final OutboundUrlGuard guard = new OutboundUrlGuard(/* allowPrivateTargets */ false);

    @ParameterizedTest
    @ValueSource(strings = {
        "http://169.254.169.254/latest/meta-data/",  // cloud metadata
        "http://metadata.google.internal/",
        "http://localhost:8080/health",
        "http://127.0.0.1/",
        "http://10.0.0.5/",                            // private
        "http://[fc00::1]/"                            // IPv6 unique-local
    })
    void blocksSsrfTargets(String url) {
        assertThatThrownBy(() -> guard.check(URI.create(url)))
            .isInstanceOf(BlockedTargetException.class);
    }

    @Test
    void allowsPublicHttps() {
        assertThatCode(() -> guard.check(URI.create("https://api.example.com/agent")))
            .doesNotThrowAnyException();
    }

    @Test
    void devProfile_canOptIntoPrivateTargets() {
        var devGuard = new OutboundUrlGuard(/* allowPrivateTargets */ true);
        assertThatCode(() -> devGuard.check(URI.create("http://localhost:8080/")))
            .doesNotThrowAnyException();
    }
}
```

### 2.4 Example — service unit test with mocked RBAC

```java
@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    @Mock OrganizationAccessService access;
    @Mock DatasetRepository datasets;
    @Mock ProjectRepository projects;
    @InjectMocks DatasetService service;

    @Test
    void createDataset_requiresMemberRole() {
        var actor = UUID.randomUUID();
        var project = aProjectIn(/* org */ UUID.randomUUID());
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(datasets.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createDataset(actor, project.getId(), new CreateDatasetRequest("metrics", null, CSV));

        verify(access).requireRole(actor, project.getOrganizationId(), OrganizationRole.MEMBER);
        verify(datasets).save(argThat(d -> d.getOrganizationId().equals(project.getOrganizationId())));
    }
}
```

---

## 3. Slice tests

### 3.1 `@DataJpaTest` with Testcontainers PostgreSQL

> **Why Testcontainers, not H2:** the schema is owned by **Flyway** and the app runs `spring.jpa.hibernate.ddl-auto=validate`. Tests must run against the **real Flyway-migrated PostgreSQL schema** so column types, constraints, and Specifications behave as in production. H2 would diverge.

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // do NOT swap in H2
@Testcontainers
class DatasetRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true); // real schema
    }

    @Autowired DatasetRepository datasets;

    @Test
    void findByTuple_returnsOnlyOwnedRow() {
        var org = UUID.randomUUID();
        var project = UUID.randomUUID();
        var saved = datasets.save(Dataset.create("d1", project, org));

        assertThat(datasets.findByIdAndProjectIdAndOrganizationId(saved.getId(), project, org))
            .isPresent();

        // foreign org -> empty (the IDOR-as-404 contract at the data layer)
        assertThat(datasets.findByIdAndProjectIdAndOrganizationId(saved.getId(), project, UUID.randomUUID()))
            .isEmpty();
    }
}
```

A single shared Testcontainers base class (a `@Container` declared `static` and reused, or Testcontainers' singleton pattern) keeps the suite fast.

### 3.2 `@WebMvcTest` with `spring-security-test`

Controllers are tested in isolation; the service layer is mocked. Security filters are active, so RBAC/authentication behavior is asserted at the HTTP boundary.

```java
@WebMvcTest(DatasetController.class)
@Import(SecurityConfig.class)
class DatasetControllerTest {

    @Autowired MockMvc mvc;
    @MockBean DatasetService datasetService;

    @Test
    void list_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/projects/{p}/datasets", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void create_validatesBody() throws Exception {
        mvc.perform(post("/api/v1/projects/{p}/datasets", UUID.randomUUID())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")) // blank name -> validation error
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
```

---

## 4. Integration / system tests

`@SpringBootTest` + Testcontainers exercise the **full request → DB** path. Reserve these for high-value, multi-layer flows (e.g. "create dataset → import rows → run evaluation job → read summary").

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
class EvaluationFlowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("broksforge.agent.health.allow-private-targets", () -> false);
    }

    @Autowired MockMvc mvc;

    @Test
    void evaluationJob_producesPaginatedRunsAndSummary() throws Exception {
        // ... authenticate, create dataset+prompt+profile, start job, poll, assert summary + paged runs
    }
}
```

Name integration tests `*IT` so the build can run them in a separate (slower) phase from unit `*Test`.

---

## 5. Security tests (mandatory per module)

These encode the controls from `SECURITY_GUIDE.md` as executable assertions.

### 5.1 RBAC

```java
@Test
@WithMockUser
void member_cannotPerformOwnerOnlyAction() throws Exception {
    doThrow(new ForbiddenException(ErrorCode.INSUFFICIENT_ROLE))
        .when(access).requireRole(any(), any(), eq(OrganizationRole.OWNER));

    mvc.perform(delete("/api/v1/organizations/{o}", UUID.randomUUID()).with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
}
```

### 5.2 IDOR via foreign id → 404

```java
@Test
@WithMockUser
void foreignDatasetId_resolvesTo404_notForbidden() throws Exception {
    when(datasetService.get(any(), any(), any()))
        .thenThrow(new NotFoundException(ErrorCode.DATASET_NOT_FOUND)); // tuple miss

    mvc.perform(get("/api/v1/projects/{p}/datasets/{d}", UUID.randomUUID(), UUID.randomUUID()))
        .andExpect(status().isNotFound()); // never 403 — do not leak existence
}
```

### 5.3 SSRF blocked at execution

```java
@Test
void evaluationExecution_blocksMetadataEndpoint() {
    var blocked = URI.create("http://169.254.169.254/latest/meta-data/");
    assertThatThrownBy(() -> agentCaller.callAgent(blocked, Map.of()))
        .isInstanceOf(BlockedTargetException.class);
    verify(httpClient, never()).send(any(), any()); // guard runs BEFORE the network call
}
```

### 5.4 Validation & mass-assignment

```java
@Test
@WithMockUser
void create_ignoresServerControlledFields() throws Exception {
    // Even if a client sends organizationId/status, the DTO has no such field to bind.
    mvc.perform(post("/api/v1/projects/{p}/datasets", UUID.randomUUID())
            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"d\",\"format\":\"CSV\",\"organizationId\":\"" + UUID.randomUUID() + "\",\"status\":\"ARCHIVED\"}"))
        .andExpect(status().isCreated());

    verify(datasetService).createDataset(any(), any(),
        argThat(req -> /* record type simply has no organizationId/status accessor */ true));
}
```

### 5.5 No secret leakage in responses

```java
@Test
@WithMockUser
void credentialResponse_neverContainsRawSecret() throws Exception {
    mvc.perform(get("/api/v1/projects/{p}/agents/{a}/credential", UUID.randomUUID(), UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.secret").doesNotExist())     // no raw secret
        .andExpect(jsonPath("$.secretHint").exists())        // only a masked hint
        .andExpect(jsonPath("$.keyVersion").exists());
}
```

Also assert (unit level) that **logs never contain** secret values for credential-set operations.

---

## 6. Contract / OpenAPI tests

- The OpenAPI document is generated from the controllers; a CI check **diffs** the generated spec against the committed one and **fails on unreviewed drift**.
- Validate responses against the schema (e.g. via a schema-validation step) so error envelopes (`code`, `message`) stay stable for clients.
- Treat the OpenAPI contract as part of the public API surface — breaking changes require a version bump.

---

## 7. Frontend testing notes (Next.js 15)

| Layer | Tooling | What |
|-------|---------|------|
| Unit / component | **Vitest/Jest + React Testing Library** | components, hooks, data formatting, accessible roles |
| Data layer | **MSW** (Mock Service Worker) | mock API contracts; assert loading/error/empty states |
| E2E | **Playwright** | critical flows: login, create project, run evaluation, view report |
| a11y | `@axe-core/playwright` | no critical accessibility violations on key pages |

- Test **pagination, query caching, and error boundaries** explicitly — they are core to the product's performance story (see `PERFORMANCE_GUIDE.md`).
- Render-escaping test: feed `<script>` / `=cmd()` strings into report/preview components and assert they are shown as text (mirrors the XSS / CSV-injection controls).

---

## 8. Test data & fixtures

- **Builders / object mothers** (e.g. `aProject()`, `aDataset()`, `anEvaluationJob()`) over hand-rolled literals — readable, ownership-correct by default.
- Each test creates its **own** org/project; never rely on data left by another test. `@DataJpaTest` rolls back per test; integration tests should clean or use fresh schema/containers.
- **Never** commit real secrets/keys. Encryption tests use a clearly-marked **test-only 32-byte key**.
- Deterministic clocks/UUIDs where assertions depend on them (inject `Clock`/`Supplier<UUID>`).

---

## 9. Coverage expectations & "must test" gate

Coverage is a floor, not a goal. Suggested gates (JaCoCo for Java, Vitest coverage for frontend):

- **Service layer:** ≥ 85% line / branch.
- **Security-critical components** (`OutboundUrlGuard`, `CredentialEncryptionService`, access guards, metric evaluators): ≥ 95%, **including negative paths**.
- **Overall backend:** ≥ 80%.

For **every new module**, the following MUST exist before merge:

- [ ] Service unit tests covering happy path + each business rule + edge cases.
- [ ] `@DataJpaTest` (Testcontainers) for every custom repository query / Specification, including a **tuple-miss → empty** case.
- [ ] `@WebMvcTest` asserting auth required, validation errors, and error-code envelope.
- [ ] **Security tests:** RBAC, **IDOR foreign-id → 404**, validation/mass-assignment, and — if it dials agents — **SSRF-blocked** and **no-secret-leak**.
- [ ] At least one `@SpringBootTest` IT for the module's primary end-to-end flow.
- [ ] OpenAPI spec updated and contract check green.

---

## 10. CI guidance

```text
Pipeline stages (fail-fast):
  1. build + compile            (mvn -q -B compile)
  2. unit tests (*Test)         fast; run on every push/PR
  3. slice + IT (*IT)           Testcontainers (Docker available on the runner)
  4. coverage gate              JaCoCo thresholds enforced
  5. static analysis            CodeQL / SpotBugs + find-sec-bugs
  6. dependency + secret scan   Dependency-Check/Dependabot + gitleaks
  7. OpenAPI contract diff      fail on unreviewed drift
  8. frontend: lint + vitest + (nightly) Playwright E2E
```

- Runners **must have Docker** for Testcontainers; pin image tags (`postgres:16-alpine`) for reproducibility.
- Reuse one Postgres container across the JPA suite (Testcontainers singleton) to keep wall-clock low.
- Keep unit tests on the fast PR path; gate merges on stages 1–7.
- Treat a flaky security test as a **blocker**, not a nuisance — quarantine is not acceptable for IDOR/SSRF/secret-leak tests.

> **CI is now implemented as GitHub Actions workflows.** The pipeline above (§10) maps onto the
> committed workflows: **`backend-ci`** (build + unit + slice/IT with Testcontainers + JaCoCo gate +
> OpenAPI contract diff), **`frontend-ci`** (lint + type-check + Vitest, with Playwright on the nightly
> path), **`codeql`** (static analysis / code scanning), **`docker`** (build the backend & frontend
> images and a compose smoke-up), **`dependency-review`** (flag risky dependency changes on PRs), and
> **`release`** (tag-driven build of the v1.0.0 artifacts/images). These automate the same stages — the
> manual checklists below remain the human gate for things CI cannot fully assert (UI walkthrough,
> release sign-off).

---

## 11. Integration testing strategy

Integration tests prove that the **layers wire together against the real schema** — not just that each
unit is correct in isolation. They are the **fewest** tests (top of the pyramid) and the **most
valuable per test**, so they are reserved for flows that cross module and infrastructure boundaries.

**Scope & principles**

- **Real PostgreSQL via Testcontainers, real Flyway schema.** Never H2 — `ddl-auto=validate` against
  the Flyway-migrated `postgres:16-alpine` is the whole point (see §3.1). Pin the image tag for
  reproducibility.
- **One container per suite.** Reuse a single Postgres container across the integration suite
  (Testcontainers singleton / shared `@Container static`) to keep wall-clock low.
- **`@SpringBootTest` + `@AutoConfigureMockMvc`** drives the **full `controller → service → repository →
  DB`** path through the security filter chain, so auth, RBAC, validation and the `ApiError` envelope
  are all exercised end to end.
- **No real network egress.** Outbound agent/model calls are **stubbed** (a `MockWebServer` / WireMock
  or a test `ModelInvoker`) and `allow-private-targets` stays **false** by default so the SSRF guard is
  also under test. A dedicated test may flip it to `true` to assert the *opt-in* path.
- **Name `*IT`** so the build runs them in a separate, slower phase from unit `*Test` (Failsafe vs
  Surefire), keeping the PR path fast.

**The integration flows that matter most (each is one `*IT`):**

| Flow | What it proves |
|------|----------------|
| **Auth lifecycle** | register → login → access protected route → refresh (rotation) → logout revokes |
| **Tenant isolation** | a user in org A gets **404** for an org-B resource id at the HTTP boundary (IDOR-as-404) |
| **Agent onboarding** | register agent → add+activate version → set credential (secret never returned) → health check honours the SSRF guard |
| **Evaluation end to end** | create dataset+prompt+profile → start job (stubbed agent) → poll → assert **paginated runs** + **precomputed summary** |
| **Benchmark / regression** | two completed jobs → leaderboard ordering, and baseline-vs-candidate regression findings |
| **Report export safety** | export a job report and assert CSV-formula / HTML-XSS payloads are neutralised |
| **Advisor on read (P4)** | seed jobs → `GET …/advisor` returns ranked recommendations and is **idempotent** (nothing persisted beyond bounded `occurrence_count` bumps) |
| **Observability (P5)** | every response carries `X-Correlation-Id` / `X-Request-Id`; `GET /actuator/health/readiness` reflects DB state; `GET /actuator/prometheus` is **ADMIN-guarded** (401/403 without an admin token) |

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ObservabilityEndpointsIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;

    @Test
    void everyResponse_carriesCorrelationId() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void prometheusEndpoint_requiresAdmin() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isUnauthorized()); // ADMIN-guarded by SecurityConfig
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void prometheusEndpoint_servesMetricsForAdmin() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("http_server_requests")));
    }
}
```

---

## 12. Smoke testing strategy (post-deploy)

A **smoke test** answers one question after every deploy: *is this build alive and serving the golden
path?* It is **fast, shallow and read-mostly**, runs against a **running instance** (not Testcontainers),
and **gates the rollout** — a failed smoke test rolls back. It is automated in the `docker` CI workflow
(compose up → smoke) and re-run against staging/prod on deploy.

Run in order; stop on the first failure:

1. **Liveness & readiness.** `GET /actuator/health` is `UP`; `GET /actuator/health/readiness` is `UP`
   (proves the **DB is reachable** — readiness includes `db`); `GET /actuator/health/liveness` is `UP`.
2. **Auth golden path.** Register a throwaway user (or log in a seeded smoke account) → receive an
   access token → `GET /api/v1/users/me` returns it. Confirms DB writes, JWT issue and validation.
3. **Create org + project.** `POST /organizations` (caller becomes `OWNER`) → `POST …/projects`.
   Confirms tenant write path and RBAC.
4. **Run a tiny evaluation.** Create a 1–2 row dataset version + a trivial prompt + an `EXACT_MATCH`
   profile, then start an evaluation job against a **stub/echo agent** and poll until `COMPLETED`.
   Confirms the executor, outbound-call path (SSRF guard with `MODEL_ALLOW_PRIVATE_TARGETS` in the
   smoke env), metric scoring, and the precomputed summary.
5. **Metrics endpoint.** `GET /actuator/prometheus` **with an admin token** returns `200` and contains
   `http_server_requests` and `hikaricp_connections_active`. Confirms the P5 metrics surface is live
   and the ADMIN guard works.
6. **Correlation header.** Assert any response carries `X-Correlation-Id` (operability is part of the
   contract).

> Keep the smoke suite **under ~60 seconds** and **idempotent** (uses disposable tenants, or cleans up).
> It validates *deployment health*, not feature correctness — that is what §4/§11 are for.

---

## 13. Release checklist (the V1 gate)

All boxes must be green before tagging a release (`v1.0.0` and onward). This is the human + CI gate.

**Code & CI**

- [ ] `backend-ci` green: compile, **all unit + slice + `*IT` tests pass**, JaCoCo thresholds met
      (service ≥ 85%, security-critical ≥ 95%, overall ≥ 80% — see §9).
- [ ] `frontend-ci` green: lint, type-check, Vitest; **Playwright** critical-flow run green.
- [ ] `codeql` green (no new high/critical alerts); SpotBugs + find-sec-bugs clean.
- [ ] `dependency-review` clean (no newly introduced vulnerable/incompatible-licence deps);
      `gitleaks` reports **no secrets**.
- [ ] **OpenAPI contract diff** reviewed — any change is intentional and, if breaking, carries a
      version bump.
- [ ] `docker` workflow builds **both** images and the compose smoke-up passes.

**Data & config**

- [ ] **Flyway migrations are append-only** (`V1`…`V25`); no applied migration was edited;
      `mvn flyway:validate` (or boot-time validation) passes on a clean volume.
- [ ] App **fails fast** when `JWT_SECRET` / `ENCRYPTION_KEY` are absent (verified, not assumed).
- [ ] `.env.example` documents every required variable; no real secret is committed.
- [ ] `allow-private-targets` flags default to **`false`** in the release config.

**Docs & artifacts**

- [ ] `README`, `ROADMAP`, and `MASTER_ARCHITECTURE` reflect the shipped scope; version strings
      (`info.app.version`, badges) read **1.0.0**.
- [ ] `release` workflow produces the tagged artifacts/images and the changelog.

**Sign-off**

- [ ] **Smoke test (§12)** passes against the release candidate in a staging environment.
- [ ] **Manual verification (§14)** completed by a reviewer.
- [ ] **Regression checklist (§15)** re-verified — no Phase 1–4 behaviour changed.

---

## 14. Manual verification checklist (UI walkthrough)

Some things only a human catches — visual regressions, focus traps, confusing copy, an action that
"works" in the API but feels wrong in the UI. Walk the product once per release (this mirrors the
README *Verifying the build* steps; do it in a **fresh** environment).

- [ ] **Auth.** Register → land on dashboard. Log out, log back in. *Forgot password* → grab the link
      from backend logs → reset → log in with the new password.
- [ ] **Email verification.** Trigger it; confirm the link works (link is in the backend logs in dev).
- [ ] **Organizations & members.** Create an org (you are `OWNER`); add a second user by email; change
      their role; remove them. Confirm a `MEMBER` cannot perform owner-only actions in the UI.
- [ ] **Projects & API keys.** Create a project; create an API key and confirm it is shown **once**;
      revoke it.
- [ ] **Agents.** Register an agent; search/filter/paginate the list; open detail and walk
      **Overview / Versions / Health / Credentials / Settings**. Add + activate a version, then add
      another and **rollback**. Set a credential and confirm only a **masked hint** is ever shown again.
      Run a **health check** (dev: `AGENT_HEALTH_ALLOW_PRIVATE_TARGETS=true`).
- [ ] **Datasets & prompts.** Import a CSV/JSON dataset version; view items + stats. Create a prompt,
      add a `{{variable}}` version, **activate**, add another, **compare** and **rollback**.
- [ ] **Evaluation.** Create a profile; create + run a job; inspect **summary, paginated runs, per-run
      results**.
- [ ] **Benchmark / regression / analytics / reports.** Build a leaderboard from two jobs; run a
      baseline-vs-candidate regression; view analytics trends and the dashboard; export a report
      (JSON/CSV/HTML) and confirm it opens.
- [ ] **Advisor / root-cause / AI debugger (P4).** Open the project **Advisor**; open agent- and
      prompt-scoped advice (each recommendation shows why / what changed / how to fix / expected
      improvement / confidence / severity). On a failed job open **Root cause**; on a run open **Debug**
      and confirm the timeline marks uninstrumented stages honestly as `NOT_INSTRUMENTED`.
- [ ] **Knowledge graph (P4).** Browse the seeded catalogue and a node's neighbours.
- [ ] **Cross-cutting UI.** Dark mode renders; layout is responsive at mobile/tablet/desktop widths;
      protected routes redirect when logged out; token refresh is transparent (no surprise logout); no
      console errors; key pages pass an `@axe-core` a11y check.

---

## 15. Regression checklist (re-verify each release)

Phases are **additive** — a release must not change Phase 1–4 behaviour. Re-verify these invariants
every release (most are already asserted by §5/§11 tests; this is the explicit "did we break a
guarantee" list):

- [ ] **Tenant isolation / IDOR.** A foreign `(id, projectId, organizationId)` still resolves to **404**,
      never 403 and never another tenant's data — across **every** resource (agents, datasets, prompts,
      evaluations, benchmarks, regressions, reports, advisor/root-cause/debugger).
- [ ] **RBAC.** `OWNER > ADMIN > MEMBER` and `USER`/`ADMIN` boundaries still hold; owner-only actions
      still reject members with `INSUFFICIENT_ROLE`.
- [ ] **SSRF.** `OutboundUrlGuard` still blocks loopback/private/link-local/metadata for **health,
      model and evaluation** calls; the guard still runs **before** any network send; `allow-private-targets`
      defaults to `false`.
- [ ] **Secret handling.** Credentials remain **write-only** — no raw secret in any response or log;
      only a masked hint + `keyVersion` is returned. Encryption round-trips and tamper still fails the
      GCM tag.
- [ ] **Reproducibility.** Dataset/prompt/agent **versions stay immutable**; a job pinned to a version
      reads the same inputs as when it ran.
- [ ] **Advisor purity (P4).** Advisory reports and root-cause findings are still **computed on read**
      (no recommendation tables); the only write is the bounded, best-effort `occurrence_count` bump.
- [ ] **Error contract.** The `ApiError` envelope (`code`, `message`, …) and the **OpenAPI** surface are
      unchanged for existing endpoints (contract diff is clean or version-bumped).
- [ ] **Migrations.** `V1`…`V25` are append-only and validate on a clean volume; no checksum drift.
- [ ] **Observability (P5).** Correlation/request IDs still appear on responses and log lines;
      readiness still includes `db`; `/actuator/prometheus` is still ADMIN-guarded.
- [ ] **Performance invariants.** Every list endpoint is still paginated; outbound calls still run
      **outside** a held DB transaction (see `PERFORMANCE_GUIDE.md` §4).
