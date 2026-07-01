# Security Guide — Brok's Forge

- Audience: engineers, reviewers, on-call, and security auditors of the Brok's Forge platform.
- Scope: the Java 21 / Spring Boot 3.4.1 modular-monolith backend (PostgreSQL + Flyway, Redis) and the Next.js 15 frontend.
- Status: living document. Last reviewed 2026-07-01 (Phase 5: production hardening — metrics/structured logging/probes — and the Version 1.0.0 release audit, on top of Phase 4: AI Engineering Advisor, root-cause analysis, knowledge graph, AI Debugger, tracing seam; Phase 3: dataset, prompt, model-provider SPI, evaluation, benchmark, regression, analytics, report, search, dashboard).
- Related ADRs:
  - [ADR-0003 — Encrypt agent credentials instead of hashing](./adr/0003-credential-encryption-vs-hashing.md)
  - [ADR-0004 — SSRF protection for outbound agent calls](./adr/0004-ssrf-protection-for-agent-endpoints.md)
  - [ADR-0013 — Engineering Knowledge Graph](./adr/0013-engineering-knowledge-graph.md)
  - [ADR-0014 — AI Debugger execution timeline and the tracing seam](./adr/0014-ai-debugger-and-tracing-seam.md)
  - [ADR-0015 — Production observability: Prometheus metrics and structured logging](./adr/0015-production-observability-metrics-and-structured-logging.md)
- Public-facing summary for reporters: [SECURITY.md](../SECURITY.md) (supported versions + how to report); this guide is the engineering detail.

This guide describes the controls **that exist today** and, separately and honestly, the **known limitations and hardening roadmap**. Where a control is aspirational it is labelled *Recommended* or *Future*.

---

## 1. Threat model overview

Brok's Forge is a multi-tenant SaaS platform. Tenants are modelled as `Organization → Project`, and every business aggregate (agent, dataset, prompt, evaluation, benchmark, report, …) is owned by a `(project, organization)` pair. The platform also makes **outbound** HTTP calls to user-registered agent endpoints, which is itself a first-class attack surface.

### Assets to protect

| Asset | Why it matters |
|-------|----------------|
| User credentials & session tokens | Account takeover |
| Agent usage secrets (API keys, bearer/basic/header credentials) | Lateral movement into customer systems |
| Tenant data (datasets, prompts, evaluation results, reports, analytics) | Confidentiality, cross-tenant leakage |
| Encryption / signing keys | Compromise of all of the above |
| The platform's outbound request capability | SSRF / confused-deputy into internal infrastructure |

### Trust boundaries

1. **Internet → edge** — TLS termination, CORS, security headers.
2. **Edge → application** — authenticated, authorized requests only.
3. **Application → PostgreSQL / Redis** — internal, network-isolated.
4. **Application → agent endpoints** — *outbound to untrusted, user-supplied URLs* (the SSRF boundary).

### Primary threats (STRIDE-flavoured) and the control that answers each

| Threat | Example | Primary control |
|--------|---------|-----------------|
| Spoofing | Forged identity | JWT (signed) + API keys (hashed) |
| Tampering | Altered ciphertext, altered request | AES-256-GCM auth tag; Bean Validation; JPA binding |
| Repudiation | "I didn't do that" | Correlation/request IDs + actor/resource audit logging |
| Information disclosure | Cross-tenant read, secret leak, stack trace | `(id, project, org)` tuple guards; write-only secrets; `GlobalExceptionHandler` |
| Denial of service | Unbounded import, resource-holding eval | Size/shape validation; pagination; *rate limiting (Future)* |
| Elevation of privilege | MEMBER acting as OWNER | `OrganizationAccessService.requireRole` RBAC |
| SSRF (platform-specific) | Agent URL → `169.254.169.254` | `@ValidEndpointUrl` + `OutboundUrlGuard` |
| Injection | SQLi, CSV/formula, stored XSS | Parameter binding; export encoding; HTML escaping |

---

## 2. Authentication

Two first-class authentication mechanisms, both stateless at the application layer.

### 2.1 JWT bearer (interactive users)

- **Algorithm:** HMAC (HS, symmetric) via `jjwt`. Signing secret comes from the environment, **never hardcoded**.
- **Token pair:** short-lived **access** token + longer-lived **refresh** token, with **refresh rotation** (each refresh issues a new refresh token).
- **Passwords:** stored with **BCrypt** (adaptive, salted). Never logged, never returned.
- **Account lifecycle tokens:** email-verification and password-reset tokens are high-entropy and stored **hashed** (SHA-256), so a database read does not yield a usable token.

### 2.2 API keys (programmatic clients)

- Format: a public **`bf`** prefix + a high-entropy secret.
- The secret is stored **hashed with SHA-256**; the raw key is shown **once** at creation and never again.
- Lookups match by prefix, then verify the hash — the database never holds a usable secret.

### 2.3 Rules

- All controllers require `@PreAuthorize("isAuthenticated()")`; there are no anonymous business endpoints.
- Authentication failures return a stable `ErrorCode` (see §10), never a hint about *why* (no user-enumeration).
- Keys/secrets are **write-only** over the API — responses carry metadata and masked hints only.

---

## 3. Authorization & RBAC

Authorization is enforced **in the service layer**, not only at the controller, so it cannot be bypassed by an alternate call path.

### 3.1 Role model

`OrganizationRole`: **OWNER > ADMIN > MEMBER**, compared with `isAtLeast(...)`. Higher roles inherit the capabilities of lower roles.

```java
// Conceptual model of the comparison used by requireRole
public enum OrganizationRole {
    MEMBER, ADMIN, OWNER;

    public boolean isAtLeast(OrganizationRole required) {
        return this.ordinal() >= required.ordinal();
    }
}
```

### 3.2 Enforcement entry points

`OrganizationAccessService` is the single authority:

- `requireMembership(userId, organizationId)` — caller belongs to the org at all.
- `requireRole(userId, organizationId, MINIMUM_ROLE)` — caller has at least the required role.

```java
// Read paths require membership; mutating paths require a minimum role.
public DatasetResponse createDataset(UUID actorId, UUID projectId, CreateDatasetRequest req) {
    var project = projectRepository.findById(projectId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.PROJECT_NOT_FOUND));
    organizationAccessService.requireRole(actorId, project.getOrganizationId(), OrganizationRole.MEMBER);
    // ... proceed
}
```

### 3.3 Principle

- **Deny by default.** A request that does not pass an explicit `requireRole`/`requireMembership` check never reaches business logic.
- Choose the **least** role that the operation needs; reads should not demand ADMIN.

---

## 4. Multi-tenant isolation & IDOR protection — the `(id, project, org)` tuple

This is the cornerstone control against **cross-tenant data access** and **IDOR** (Insecure Direct Object Reference).

### 4.1 The rule

**Never load an entity by `id` alone.** Always load it by the **full ownership tuple** `(id, projectId, organizationId)`. A foreign or guessed id simply **does not match** and resolves to **404 Not Found** — the platform never confirms "this exists but isn't yours" (which would itself leak existence).

```java
// Per-aggregate access guard (pattern shared by AgentAccessGuard and every Phase 3 module)
@Component
public class DatasetAccessGuard {

    private final DatasetRepository datasets;

    public Dataset require(UUID datasetId, UUID projectId, UUID organizationId) {
        return datasets
            .findByIdAndProjectIdAndOrganizationId(datasetId, projectId, organizationId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.DATASET_NOT_FOUND)); // foreign id -> 404
    }
}
```

```java
// Repository expresses ownership in the query itself — not in post-load checks.
public interface DatasetRepository extends JpaRepository<Dataset, UUID> {
    Optional<Dataset> findByIdAndProjectIdAndOrganizationId(UUID id, UUID projectId, UUID organizationId);
}
```

### 4.2 Why a tuple and not a post-load `if`

A post-load ownership check (`load by id`, then `if (entity.org != caller.org) throw`) is fragile: it is easy to forget on one code path, and a timing/branch difference can leak existence. Encoding ownership **in the query predicate** makes the safe path the *only* path and makes "not yours" indistinguishable from "doesn't exist".

### 4.3 Scope every derived query

Analytics, search, dashboards, reports — **every** query that lists or aggregates **must** carry `organizationId` (and usually `projectId`) in its `WHERE`/Specification. There is no "global" list endpoint. See §11 for the per-endpoint checklist item that enforces this.

---

## 5. Secret management

The platform deliberately uses **two different strategies** depending on whether a secret must be *checked* or *re-presented*. The full rationale is in [ADR-0003](./adr/0003-credential-encryption-vs-hashing.md).

### 5.1 Hash vs encrypt — the decision

| Class | Examples | Strategy | Why |
|-------|----------|----------|-----|
| **Verification secrets** (we only ever *check* them) | passwords, API-key secrets, refresh/reset/verification tokens | **Hash** (BCrypt for passwords, SHA-256 for high-entropy tokens) | One-way; a DB leak reveals nothing usable |
| **Usage secrets** (we must *re-present* them to a third party) | agent API keys, bearer tokens, basic-auth passwords, custom-header values | **Encrypt** (AES-256-GCM) | A hash cannot be sent upstream; the original must be recoverable |

> A one-way hash is unusable for class 2: you cannot authenticate to an agent with a hash. That single fact forces encryption — and the key-custody responsibility it brings.

### 5.2 AES-256-GCM details (`CredentialEncryptionService`)

- **Algorithm:** AES-256 in **GCM** (authenticated encryption → confidentiality **and** tamper-evidence).
- **IV:** fresh random **96-bit** IV per value (never reused with the same key).
- **Auth tag:** **128-bit** GCM tag; a corrupted/edited ciphertext **fails to decrypt** rather than returning garbage.
- **Key:** Base64 **32 bytes (256-bit)** supplied **only** via env `BROKSFORGE_SECURITY_ENCRYPTION_KEY`. Never hardcoded, never logged.
- **Self-describing ciphertext** enables rotation:

```text
v<keyVersion>:<base64(iv)>:<base64(ciphertext+tag)>

example:  v1:n2Vq...==:9aF3c...Q==
```

### 5.3 Key custody & rotation (via `keyVersion`)

- **Custody today:** the key lives in the process environment. Production **must** source it from a secrets manager / KMS with least-privilege access (see §13).
- **Rotation:** because every ciphertext stamps its `keyVersion`, you can introduce `v2` for new writes while still decrypting `v1` history. Re-encryption of old rows becomes a background task, **not a schema migration**.

### 5.4 Non-negotiable secret rules

- Secrets are **never** returned by the API. Responses carry only metadata: auth type, username, header name, a **masked `secretHint`**, and key version.
- Secrets are **never logged** (not in MDC, not in audit lines, not in exceptions).
- Decryption happens **only** for internal outbound calls (e.g. `resolveAuthHeaders`), never to serve a response.
- Credentials are **write-only** over the API; the agent's declared `authType` is kept aligned with its active credential.

---

## 6. SSRF defense

The platform makes outbound calls to **user-supplied** agent URLs — including, in Phase 3, **evaluation execution**. Defense is **two layers**; full rationale in [ADR-0004](./adr/0004-ssrf-protection-for-agent-endpoints.md).

### 6.1 Layer 1 — syntactic validation (`@ValidEndpointUrl`)

Runs on register/update DTOs:

- `http`/`https` **only** (rejects `file://`, `gopher://`, etc.).
- **No embedded credentials** (`http://user:pass@host`).
- **Bounded length.**
- Deliberately *allows* private hostnames to be **stored**, because an org may legitimately register an internal agent.

### 6.2 Layer 2 — runtime network-policy guard (`OutboundUrlGuard`)

Consulted **immediately before every outbound call** — health checks, invocation, and **Phase 3 evaluation/benchmark execution**. It re-validates the scheme, rejects embedded credentials, **resolves the host**, and **blocks**:

- loopback, link-local, site-local/**private**, any-local, multicast
- IPv6 **unique-local `fc00::/7`**
- known metadata hosts/addresses: `169.254.169.254`, `metadata.google.internal`, `localhost`, `*.internal`, `*.local`

```java
// Every module that calls an agent endpoint funnels through the guard first.
public HttpResponse<String> callAgent(URI endpoint, Map<String, String> authHeaders) {
    outboundUrlGuard.check(endpoint); // throws/records a policy failure if the resolved IP is blocked
    return httpClient.send(buildRequest(endpoint, authHeaders), BodyHandlers.ofString());
}
```

### 6.3 Configuration

- `broksforge.agent.health.allow-private-targets`:
  - **`false` in production** (SaaS deployments are safe by default),
  - **`true` in the `dev` profile** (local agents on `localhost`/private addresses work).
- A blocked probe is **not** a mid-operation 400; it is recorded as a **failed health check with a clear reason** ("unreachable by policy").

### 6.4 Reuse

`OutboundUrlGuard` is **the** outbound gate. Any new module that dials an agent endpoint **must** route through it — this is a checklist item in §11.

---

## 7. Input validation & mass-assignment prevention

### 7.1 Bean Validation on every request DTO

All request DTOs are validated with Jakarta Bean Validation (`@NotNull`, `@Size`, `@Email`, `@Pattern`, custom constraints like `@ValidEndpointUrl`). Controllers use `@Valid`; failures are converted by the `GlobalExceptionHandler` to a stable `VALIDATION_ERROR` response.

```java
public record CreateDatasetRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 2_000) String description,
    @NotNull DatasetFormat format
) {} // note: no ownerId / organizationId / status — see 7.2
```

### 7.2 Mass-assignment prevention

Request **records omit server-controlled fields** — `ownerId`, `organizationId`, `status`, timestamps, ids. The server sets them from the authenticated principal and the URL path, so a client **cannot** inject a foreign `organizationId` or flip `status`. This is structural: there is no field to bind.

### 7.3 Phase 3 import sizing

Dataset CSV/JSON import (§9) additionally validates **size and shape** before parsing into memory.

---

## 8. Injection prevention

- **SQL:** all data access goes through **JPA parameter binding** and the **Criteria/Specification** API. There is **no string-concatenated SQL**. Dynamic filters (search, analytics) are built as Specifications with bound parameters, never by interpolation.
- **Prompt templates with `{{variables}}`:** template variables are **data, not code**. Substitution is a string replace into the rendered prompt; the platform **never** `eval`s, compiles, or executes template content. (Extracted variable names are validated against an allowed pattern.)
- **JPQL/HQL:** parameterized only; no concatenated entity/field names from user input.

---

## 9. CSV / HTML export safety (Phase 3)

### 9.1 CSV / formula injection (datasets, analytics, regression exports)

A cell beginning with `=`, `+`, `-`, `@`, or a leading tab/CR can be interpreted as a **formula** by Excel/Sheets, enabling data exfiltration or command execution on the *recipient's* machine.

**Mitigation on export:** neutralize any cell whose first character is a formula trigger (prefix with a single quote `'` and/or wrap/escape per RFC 4180), and always quote fields containing `,`, `"`, `\n`, or leading whitespace.

```java
private static final Set<Character> FORMULA_TRIGGERS = Set.of('=', '+', '-', '@', '\t', '\r');

static String safeCsvCell(String raw) {
    String v = raw == null ? "" : raw;
    if (!v.isEmpty() && FORMULA_TRIGGERS.contains(v.charAt(0))) {
        v = "'" + v;                  // defuse formula interpretation
    }
    if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
        v = "\"" + v.replace("\"", "\"\"") + "\""; // RFC 4180 quoting
    }
    return v;
}
```

**On import:** validate declared size against a max, cap row/column counts, reject non-UTF-8 / malformed structure, and stream-parse rather than loading unbounded payloads.

### 9.2 Stored XSS in HTML reports

Report HTML export embeds tenant-controlled strings (prompt text, dataset names, model outputs). These **must be HTML-encoded** at render time so injected markup is shown as text, not executed.

- Use the template engine's **contextual auto-escaping** (do **not** use "raw"/`th:utext`/`{{{ }}}` constructs for user data).
- Set a restrictive **Content-Security-Policy** on exported/served report views.
- Treat model output as **fully untrusted** — it is frequently attacker-influenced via prompt content.

```java
static String htmlEscape(String s) {
    return s == null ? "" : s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#x27;");
}
```

---

## 10. Output & error hygiene

- A single `GlobalExceptionHandler` maps every exception to an `ApiError` carrying a **stable `ErrorCode`** and a safe message.
- **No stack traces, no internal details** leak to clients. Spring is configured `server.error.include-message`, `include-stacktrace`, `include-binding-errors`, `include-exception` = **never**.
- Reserved `ErrorCode`s (e.g. `RATE_LIMITED`) exist for forward compatibility even before the control ships.
- Error responses do not differentiate "not found" from "not yours" (§4), preventing existence/enumeration leaks.

---

## 11. Logging & auditing

- **Correlation/request IDs:** `X-Correlation-Id` / `X-Request-Id` are carried in the **MDC** through the request lifecycle for end-to-end traceability.
- **Sensitive operations** (login, key creation, credential set, role change, evaluation run) are logged with the **actor id** and **resource ids** — **never** secret values, tokens, or decrypted credentials.
- Logs are structured so an auditor can reconstruct *who did what to which resource, when*, by correlation id.
- **Never log:** passwords, JWTs, API-key secrets, agent credentials, encryption keys, full request bodies of secret-bearing endpoints.

---

## 12. Transport, CORS & security headers

- **HTTPS expected at the edge** in production; the application assumes TLS termination upstream.
- **CORS allowlist** is sourced from the environment (no wildcard `*` with credentials in production).
- **Security headers** applied: HSTS (at the edge), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` / frame-ancestors, a **Content-Security-Policy** (especially for report views), and `Referrer-Policy`.
- Cookies (if used for any session concern) are `Secure`, `HttpOnly`, `SameSite`.

---

## 13. Dependency & secret-scanning guidance

| Concern | Recommended control |
|---------|---------------------|
| Vulnerable dependencies (Java) | OWASP Dependency-Check / `dependency-check` or GitHub Dependabot on the Maven/Gradle build; fail CI on known-exploited CVEs |
| Vulnerable dependencies (Node/Next.js) | `npm audit` / Dependabot; pin and review transitive updates |
| Static analysis | CodeQL / SpotBugs + `find-sec-bugs` in CI |
| Secret leakage in commits | gitleaks / trufflehog pre-commit + CI scan; block pushes containing `BROKSFORGE_SECURITY_ENCRYPTION_KEY`-shaped material |
| Container/base image | Trivy/Grype scan of the runtime image |
| License & supply chain | Generate an SBOM (CycloneDX) per build |

Keep `BROKSFORGE_SECURITY_ENCRYPTION_KEY`, JWT secret, and DB credentials **out of source control** — they are environment/secret-manager only.

---

## 14. Per-endpoint security review checklist

Run this for **every new endpoint** before merge:

- [ ] **AuthN:** `@PreAuthorize("isAuthenticated()")` present; no anonymous access.
- [ ] **AuthZ:** service-layer `requireMembership`/`requireRole` with the **least** sufficient role.
- [ ] **Tenant isolation:** entity loaded via the **`(id, projectId, organizationId)` tuple**; foreign id → 404. No load-by-id-then-check.
- [ ] **Query scoping:** every list/aggregate/search query includes `organizationId` (and usually `projectId`).
- [ ] **Validation:** request DTO has Bean Validation; size/shape bounded (esp. imports).
- [ ] **Mass assignment:** DTO omits `ownerId`/`organizationId`/`status`/ids/timestamps.
- [ ] **Injection:** only parameter binding / Specifications; no string SQL; template vars treated as data.
- [ ] **Secrets:** no secret in request/response logs; responses carry masked hints + metadata only.
- [ ] **Outbound (if any):** call routed through `OutboundUrlGuard`; credentials decrypted only for the call; sane timeouts.
- [ ] **Export (if any):** CSV cells defused (formula injection); HTML contextually escaped (XSS); CSP set.
- [ ] **Errors:** maps to a stable `ErrorCode`; no stack trace/internal detail; no existence leak.
- [ ] **Audit:** actor + resource ids logged for mutating/sensitive ops; correlation id propagated.
- [ ] **Tests:** RBAC, IDOR-→404, validation, and (if applicable) SSRF-blocked and no-secret-leak tests exist (see `TESTING_STRATEGY.md`).

---

## 15. Known limitations & hardening roadmap

These are **accepted, understood** trade-offs today, with a concrete path forward.

| Limitation (today) | Risk | Hardening (recommended / future) |
|--------------------|------|----------------------------------|
| Encryption key lives in **process env** | Anyone with key + DB recovers secrets | Move to **KMS** with envelope encryption + per-tenant data keys + HSM-backed master; least-privilege; **rotate** (ciphertext already carries `keyVersion`) — see [ADR-0003](./adr/0003-credential-encryption-vs-hashing.md) |
| `OutboundUrlGuard` does **not** fully stop **DNS rebinding** (public IP at check time, private at connect time) | SSRF via TOCTOU | **Pin the resolved IP** for the connection, or route outbound agent traffic through an **egress proxy/allowlist** — see [ADR-0004](./adr/0004-ssrf-protection-for-agent-endpoints.md) |
| **Rate limiting covers the auth endpoints** (Redis fixed-window, per client IP; register/login/verify/forgot/reset/resend) — not yet the whole API | Abuse/DoS on non-auth endpoints; distributed abuse across many IPs | Extend to per-principal + per-endpoint budgets and an edge/API-gateway limiter; add outbound-call limits (see [ADR 0016](./adr/0016-pluggable-email-transport.md) for the auth-endpoint limiter) |
| **Synchronous** evaluation executor holds resources during outbound calls | Resource exhaustion under load | Move to **async workers / queue**; never hold a DB transaction across a network call (see `PERFORMANCE_GUIDE.md`) |
| Key material held in **process memory** | Memory disclosure | Minimize lifetime; envelope encryption so the long-lived master never leaves the KMS |

---

## 16. Phase 4 — AI Engineering Advisor security review

Phase 4 adds the advisor, root-cause, AI debugger and knowledge modules. The review below records
the surface it introduces and why it is safe under the controls already described.

### 16.1 Surface

- **Nine new endpoints, all read-only `GET`** (advisor ×3, root-cause ×2, debugger ×1, knowledge ×3).
- **No new write endpoints, no request bodies, no new outbound calls, and no new secrets.** The
  advisor/root-cause/debugger engines operate purely on data already persisted by earlier phases;
  none of them dial an agent endpoint, so they add **no new SSRF surface** and there is no
  mass-assignment surface to defend.

### 16.2 Tenant isolation is inherited, not re-implemented

- The advisor, root-cause and debugger services **never touch another module's repositories**. They
  compose the **published services** (`EvaluationService`, `AgentService`, `PromptService`,
  `RegressionService`), so the `(id, projectId, organizationId)` tuple guards and
  `requireMembership`/`requireRole` checks from §3–§4 apply unchanged. A foreign id resolves to 404,
  exactly as elsewhere.
- The debugger's single-run read loads a run by `(runId, evaluationJobId)` **only after** the parent
  job has passed the readable-job guard — so a run cannot be reached by guessing its id under someone
  else's job (IDOR-safe). The added `EvaluationService.getRun`/`sampleFailedRuns`/`metricFailureBreakdown`
  reads are all guarded by `EvaluationAccessGuard.requireReadableJob` before any row is returned.

### 16.3 Knowledge graph is reference data, not tenant data

- `knowledge_nodes`/`knowledge_edges` are **platform-global reference data** with **no organization
  or project columns** and no tenant content. The `GET /api/v1/knowledge/**` endpoints require an
  authenticated caller (§2) but carry no cross-tenant information, so global readability is correct
  and leaks nothing.
- The **learning seam** (`recordObservation`) increments a global occurrence counter via a single
  atomic `UPDATE`. It carries **no tenant data**, is **best-effort** (failures are swallowed and never
  surface to the caller), and cannot corrupt or disclose tenant state.

### 16.4 Untrusted content handling

- The debugger returns **length-bounded previews** of a run's input/output. This is tenant data,
  shown only to authorized project members (gated by the readable-job guard). Model output is treated
  as **fully untrusted** (it is frequently prompt-influenced, per §9.2): the API emits JSON (Jackson
  encodes it safely) and the frontend renders previews as **text, not HTML**.
- Root-cause and advisor evidence strings are likewise rendered as text in the UI.

### 16.5 Errors and injection

- New failure modes map to **stable `ErrorCode`s** — `DEBUG_TIMELINE_UNAVAILABLE` (run not yet
  executed), `KNOWLEDGE_PATTERN_NOT_FOUND`, `ADVISOR_INPUT_INSUFFICIENT`, `ROOT_CAUSE_INPUT_INVALID` —
  with no internal detail leaked (§10).
- The one new aggregate query (`tallyByMetric`) is a parameter-bound JPQL constructor expression;
  knowledge filters use derived queries. **No string-concatenated SQL** is introduced (§8).

### 16.6 Accepted limitation

- `recordObservation` is a **write on a `GET`** — the deliberate learning mechanism
  ([ADR-0013](./adr/0013-engineering-knowledge-graph.md)). It is bounded and best-effort. If
  read-replica routing is introduced later, advisor/root-cause `GET`s must route to the primary, or
  observation recording must move to an async/fire-and-forget path. Tracked in §15.

Every Phase 4 endpoint was run through the §14 checklist: AuthN present, AuthZ via the inherited
guards, tuple-based tenant isolation, no secrets in responses or logs, no outbound calls, and stable
error codes.

---

## 17. Phase 5 — production-hardening & Version 1.0.0 release audit

Phase 5 adds **operability** (metrics, structured logs, probes) and release infrastructure. It adds
**no business endpoints** and **no new tenant-data surface**. The review below covers the new surface
and re-confirms the Version 1.0.0 audit across the full checklist.

### 17.1 Metrics endpoint (`/actuator/prometheus`)
- The Prometheus endpoint is **exposed but remains ADMIN-guarded** by the existing rule
  `/actuator/** → hasRole('ADMIN')` (§3). Only `health` and `info` are public (for orchestrator
  probes). Scrapers authenticate, or reach the endpoint over a private management network.
- The published metrics contain **no secrets and no tenant data**. Micrometer's `http_server_requests`
  tags use the **URI template** (e.g. `/api/v1/organizations/{organizationId}/projects/{projectId}/datasets`),
  **not** the concrete ids — so no organization/project/resource id leaks into metric cardinality or
  scrape output. Metrics are JVM/HTTP/HikariCP/Flyway/process gauges only.

### 17.2 Structured logging
- Production uses Spring Boot 3.4 native **ECS JSON** logging (`docker` profile). The
  **"never log secrets"** rule (§11) is unchanged — structured output only changes the *encoding*, not
  *what* is logged. MDC `correlationId`/`requestId` become JSON fields (safe identifiers, already in
  response headers). No request bodies or credential values are logged.

### 17.3 Health probe exposure
- `health` is public so orchestrators can probe `liveness`/`readiness`. In the `docker`/production
  profile `management.endpoint.health.show-details: never`, so the public health response is just
  `{"status":"UP"}` — it reveals **no** datasource URLs, component names, or versions. The base profile
  uses `when-authorized`. Readiness includes the `db` indicator so a DB outage drains (not kills) the pod.

### 17.4 Build-context & supply-chain hygiene
- `.dockerignore` (backend + frontend) now **excludes `.env`/secret files** from the Docker build
  context, so credentials can never be baked into an image layer.
- `.gitattributes` normalises line endings (defence against CRLF-injection surprises in scripts).
- **Dependabot** (Maven, npm, GitHub Actions, Docker) and **CodeQL** (Java + TS) are wired in CI for
  continuous dependency and static-analysis scanning; `dependency-review` blocks vulnerable additions
  on PRs. This realises the §13 guidance as enforced automation.

### 17.5 Version 1.0.0 audit — re-confirmation across the checklist
No regressions; every control from §2–§14 remains in force. Summary verdict per area:

| Area | Status at v1.0.0 |
|------|------------------|
| **JWT** | HS-signed, env secret, short-lived access + rotating refresh — OK |
| **RBAC** | OWNER>ADMIN>MEMBER, service-layer `requireRole`/`requireMembership` — OK |
| **Encryption** | AES-256-GCM, versioned ciphertext, write-only, env key — OK |
| **API keys** | `bf` prefix + SHA-256 hash, shown once — OK |
| **Secrets** | env-only; never returned/logged; excluded from images — OK |
| **Headers** | CSP, frame-ancestors none, nosniff, HSTS, referrer-policy — OK |
| **SSRF** | `@ValidEndpointUrl` + `OutboundUrlGuard` on every outbound call — OK (DNS-rebinding gap tracked, §15) |
| **IDOR** | `(id, projectId, organizationId)` tuple → foreign id 404 — OK |
| **Injection** | JPA binding / Specifications; CSV-formula + HTML-XSS defused on export — OK |
| **Mass assignment** | request records omit server-controlled fields — OK |
| **Logging** | actor+resource ids, never secrets; structured in prod — OK |
| **Audit trail** | `created_by`/`updated_by`/`deleted_by` audit columns + correlation ids — OK |
| **Dependency risk** | Dependabot + CodeQL + dependency-review in CI — OK |
| **Configuration** | secrets fail-fast (no default for JWT/encryption keys); SSRF-safe defaults in prod — OK |

**Open items remain exactly as tracked in §15** (KMS-managed key, DNS-rebinding/egress proxy, rate
limiting). None is a release blocker for a self-hosted V1; all are documented with a forward path.

---

## 18. Reporting a vulnerability

Report suspected vulnerabilities **privately** via GitHub Security Advisories ("Report a
vulnerability" on the repository's **Security** tab) — see [SECURITY.md](../SECURITY.md) — or to the
security owners; do **not** open a public issue. Include affected endpoint, reproduction, and impact.
Critical issues (auth bypass, cross-tenant access, SSRF, secret disclosure) are triaged with top
priority.
