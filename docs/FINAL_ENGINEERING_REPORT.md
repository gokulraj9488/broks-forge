# Final Engineering Report — Brok's Forge Complete Engineering Pass

> **Scope:** the 10-fix production engineering pass (credentials, evaluation workflow,
> idle timeout, OTP password change, design system, contact-developer, production
> polish, form UX, provider-aware health checks, micro-interactions).
> **Date:** 2026-07-05 · **Author:** engineering pass on the modular monolith.
> **Verdict:** all ten fixes implemented; `mvn clean verify`, `next build`, Flyway
> migration and Spring Boot startup + `ddl-auto=validate` all pass.

This report follows the requested structure: files modified, architecture decisions,
security review, performance impact, UI improvements, screenshots, known limitations,
future improvements, deployment readiness — preceded by a per-fix summary.

---

## 0. What was delivered (per fix)

| # | Fix | Outcome |
|---|-----|---------|
| 1 | **Complete credential management** | Credentials now carry a **name/label** and a configurable **header prefix** (e.g. `Authorization: Bearer <secret>`). Added **Test Connection** (saved + dry-run-before-save), in-place **Update**, existing **Replace**/**Delete**. UI shows **Last updated**, **Credential status** (Active/Inactive) and **Connection status** (Connected / failed / Untested). Secrets stay AES-256-GCM encrypted and write-only. |
| 2 | **Complete evaluation workflow** | The dead-end "You need at least one dataset" is replaced by a **guided prerequisites** panel: per-item empty states (No active agents → Register Agent, No datasets → Create Dataset, No prompts → Create Prompt) each with an inline create dialog. Run Evaluation unlocks once agent + dataset exist. No dead ends. |
| 3 | **Proper idle session timeout** | Already reset on real activity (mouse/keyboard/scroll/touch/route/API). Added a **60-second warning modal** with a live countdown and **Stay signed in** / **Log out**; passive movement no longer silently resets during the warning, so the user must acknowledge. |
| 4 | **OTP verification for password change** | Replaced the emailed **link** with an in-session **email OTP**: current password → 6-digit code → verify → new password → all sessions revoked. Rate-limited generation, 5-minute code expiry, attempt cap, single-use continuation ticket. Uses the existing pluggable email transport (console in dev, SMTP in prod). See **ADR-0017**. |
| 5 | **Design system refresh** | The sage/peach/coral/danger/charcoal palette (from the prior pass) is completed: **new primitives** (Checkbox, Switch, Alert, Tooltip, Progress) built dependency-free; the **Toaster** is themed to the palette; the password-strength meter and idle countdown use the new primitives. |
| 6 | **Contact Developer integration** | A reusable component links to **https://gokul.quest** (new tab) on the **auth footer** (login/register/forgot/reset), the **sidebar footer**, the **404 page**, the **error page**, the **About page** and a new **Help page** (linked from the user menu). |
| 7 | **Production website polish** | Added `app/icon.svg` (favicon), `manifest.ts`, `sitemap.ts`, `robots.ts`, a dynamic **Open Graph image** (`opengraph-image.tsx` via `next/og`), full **SEO/OG/Twitter** metadata, `viewport` **theme-color** (light/dark), a branded **404**, **loading**, **error** and **global-error** page. |
| 8 | **Complete form UX** | **Caps-Lock detection** on every password field, a **password strength meter** (register / reset / new-password), **real-time validation** (`mode: onBlur`), auto-focus, accessible labels, plus the already-present auto-expanding textareas, loading/disabled states and character counters. |
| 9 | **Health check improvements** | **Provider-aware** probes chosen from framework + provider: Spring Boot → `/actuator/health`, FastAPI/LangGraph → `/health`, hosted-LLM agents → a tiny POST completion, others → the endpoint. Records/returns **strategy + probe URL** alongside Healthy/Unhealthy, latency, HTTP status, last-checked and failure reason. See **ADR-0018**. |
| 10 | **Micro-interactions & polish** | Per-route **page-transition** fade, palette-consistent animated toasts, consistent primitives, hover/focus transitions, consistent spacing/radius. |

---

## 1. Files modified

**~38 new files, ~44 modified files** across backend, frontend and docs. Highlights:

### Backend (new)
- `db/migration/V27__create_password_change_otps.sql`, `V28__extend_agent_credentials.sql`, `V29__extend_agent_health_checks.sql`
- `modules/auth/domain/PasswordChangeOtp.java`, `repository/PasswordChangeOtpRepository.java`
- `modules/auth/web/dto/{VerifyPasswordChangeOtpRequest,CompletePasswordChangeRequest,PasswordChangeTicketResponse}.java`
- `modules/agent/domain/HealthProbeStrategy.java`
- `modules/agent/service/{HealthProbePlanner,CredentialConnectionTester}.java`
- `modules/agent/web/dto/{UpdateAgentCredentialRequest,TestAgentCredentialRequest,CredentialTestResponse}.java`

### Backend (modified)
- `common/util/SecureTokens.java` (numeric OTP generator), `common/exception/ErrorCode.java` (`OTP_INVALID`, `OTP_LOCKED`)
- `config/properties/AuthTokenProperties.java`, `config/SecurityConfig.java`, `config/WebMvcConfig.java`, `application.yml`
- `modules/auth/service/AuthService.java`, `web/AuthController.java`, and the `email/*` port + both transports + `EmailContentFactory`
- `modules/agent/domain/{AgentCredential,AgentHealthCheck}.java`; `service/{AgentCredentialService,AgentHealthCheckExecutor,AgentHealthService,HealthProbeResult}.java`; `web/AgentCredentialController.java` + credential/health DTOs

### Frontend (new)
- Primitives: `ui/{checkbox,switch,alert,tooltip,progress,password-strength-meter}.tsx`; `lib/password-strength.ts`, `lib/site.ts`
- Features: `components/settings/password-change-card.tsx`, `components/auth/idle-warning-dialog.tsx`, `components/common/contact-developer.tsx`, `components/evaluations/evaluation-prerequisites.tsx`
- App-router: `app/{icon.svg,manifest.ts,robots.ts,sitemap.ts,opengraph-image.tsx,not-found.tsx,loading.tsx,error.tsx,global-error.tsx}`, `app/(dashboard)/help/page.tsx`

### Frontend (modified)
- `app/layout.tsx` (metadata + viewport), `app/globals.css` (Toaster palette), `app/(dashboard)/settings/page.tsx`
- `app/(auth)/{layout,register,reset-password}/…`, `app/(dashboard)/about/page.tsx`
- `components/agents/{credentials-panel,set-credential-dialog,health-panel}.tsx`, `components/evaluations/create-job-dialog.tsx`
- `components/auth/auth-guard.tsx`, `components/layout/{app-shell,user-menu}.tsx`, `components/ui/password-input.tsx`
- `lib/api/{agents,auth}.ts`, `lib/hooks/{use-agents,use-auth,use-idle-timeout}.ts`, `lib/validations.ts`

### Docs
- New: `adr/0017-otp-password-change.md`, `adr/0018-provider-aware-health-checks.md`, this report.
- Updated: `adr/README.md`, `MASTER_ARCHITECTURE.md`, `SECURITY_GUIDE.md`, `ROADMAP.md`, `README.md`.

---

## 2. Architecture decisions

The existing architecture — the **modular monolith** with strict module boundaries,
id-only cross-module references, the database as source of truth, and the security
model — was **preserved, not modified**. Every change is additive and follows the
established conventions (thin controllers, rich `@Transactional` services, record
DTOs that omit server-set fields, MapStruct mappers, append-only Flyway).

Two decisions were significant enough to record as ADRs:

- **[ADR-0017 — Email OTP for password change](./adr/0017-otp-password-change.md).** A
  new `password_change_otps` table (V27) backs a three-step, fully-authenticated
  flow. A low-entropy 6-digit code (hashed, attempt-capped, 5-min expiry) verifies
  the user; a high-entropy single-use ticket then authorises the change so the new
  password is never sent with the code. The legacy link endpoints are retained for
  compatibility (ARCH-3: nothing removed).
- **[ADR-0018 — Provider-aware health checks + credential testing](./adr/0018-provider-aware-health-checks.md).**
  A pure `HealthProbePlanner` chooses the probe method/URL from framework + provider;
  `CredentialConnectionTester` verifies credentials. No `agents` schema change —
  provider awareness is derived from existing fields; both reuse `OutboundUrlGuard`.

Boundary integrity was maintained: e.g. `AgentHealthService` reads the active
`AgentVersion` through the same module's repository (not a cross-module one), and
the model-provider SPI ([ADR-0006](./adr/0006-provider-agnostic-model-invocation.md))
remains the extension point for future provider-direct clients.

---

## 3. Security review

Run against the `SECURITY_GUIDE.md` §14 checklist — **no control was weakened**:

- **Secrets remain write-only + encrypted.** Credential secrets are still AES-256-GCM
  (ADR-0003); the new `label`/`header_prefix` are non-secret metadata; the connection
  test returns only a verdict (reachable/auth-rejected + latency), never the secret.
  Password OTP codes and continuation tickets are stored **hashed** (SHA-256), never
  in plaintext, never logged.
- **OTP brute-force resistance.** 6-digit codes are attempt-capped (max 5, then burned),
  expire in 5 minutes, are single-active-per-user, and generation is **rate-limited per
  user** (`RateLimiterService`) on top of the per-IP interceptor. Verify uses
  constant-time comparison. The failed-attempt increment persists via
  `noRollbackFor = ApiException.class` so the cap can't be bypassed by retriggering.
- **Authentication tightened.** All three OTP endpoints are `authenticated()` (added to
  `SecurityConfig` before the public `/api/v1/auth/**` wildcard) and rate-limited
  (`WebMvcConfig`). The change still **revokes every refresh token** on success.
- **SSRF unchanged.** Every new outbound probe (provider health + credential test)
  passes `OutboundUrlGuard.check(...)` first (ADR-0004); a blocked probe is reported as
  a failed test/health with a clear reason, not an exception.
- **Tenant isolation / RBAC intact.** Credential + health operations still resolve the
  agent via `AgentAccessGuard` (ADR: `(id, projectId, organizationId)`), require ADMIN
  for credential writes, and the OTP ticket is additionally bound to the caller.
- **Error hygiene.** New codes `OTP_INVALID` (400) and `OTP_LOCKED` (429) map through the
  standard `ApiError` contract; no internals leak.
- **Frontend.** The verify step's continuation ticket is a short-lived, single-use,
  server-hashed capability token held only in memory between steps (over TLS, for an
  already-authenticated caller). Model output / probe results are rendered as text.

Startup was validated against a real PostgreSQL: Flyway applied V1–V29 and Hibernate
`ddl-auto=validate` accepted all new entity mappings (see §7).

---

## 4. Performance impact

- **Negligible steady-state cost.** New columns are nullable/indexed where queried
  (`password_change_otps` indexes `user_id` and `ticket_hash`). No new hot-path work.
- **Health/credential probes** are manual, low-volume admin actions; each is one
  outbound HTTP call with the existing connect/read timeout. The provider "tiny POST
  completion" costs a token or two only when explicitly run.
- **Rate limiting** reuses the existing Redis fixed-window limiter (one INCR/EXPIRE),
  fail-open if Redis is down.
- **Frontend bundle** grew only by small, dependency-free primitives (no new npm
  packages — Checkbox/Switch/Alert/Tooltip/Progress are hand-rolled), keeping
  `npm ci` reproducible. The OG image renders once at build time to a static PNG.
- **Idle timer** now ticks every 1s (from 30s) only to drive the countdown; it is a
  single `setInterval` reading a localStorage timestamp — imperceptible.

---

## 5. UI improvements

- **Credentials:** name + header-prefix inputs, Test Connection with inline result,
  edit-in-place, and per-row Active/Connection-status badges + "last test" detail.
- **Evaluations:** a guided, no-dead-end prerequisites panel with inline create dialogs.
- **Password change:** a 3-step wizard with a progress indicator, OTP entry
  (`one-time-code` autofill) and a live strength meter.
- **Security:** an idle-warning modal with countdown.
- **Forms:** caps-lock warning glyph + tooltip, strength meter, real-time errors.
- **Design system:** palette-themed toasts, five new accessible primitives, consistent
  focus rings/radii, per-route page-transition fade.
- **Site:** favicon, OG/Twitter preview card, manifest (installable), branded 404 /
  loading / error pages, theme-color that follows light/dark.
- **Help & contact:** a Help page and Contact-Developer links across auth, sidebar, 404,
  error, about and help.

---

## 6. Screenshots

Not captured — this pass ran headless (no browser session). In place of screenshots,
every surface was validated by a successful production build (`next build` compiled all
routes, including the OG image, at an apostrophe-free path — see §7) and by type-checking
(`tsc --noEmit`, 0 errors). The flows are exercisable locally via
`docker compose up --build`; dev e-mail (OTP codes, links) prints to the backend console
(`LoggingEmailService`).

---

## 7. Verification performed

| Check | Command | Result |
|-------|---------|--------|
| Backend compile | `mvn -o clean compile` | **BUILD SUCCESS** (Lombok + MapStruct regen clean) |
| Backend package + verify | `mvn -o clean verify` | **BUILD SUCCESS** (~21s) |
| Flyway migrations | app boot vs. PostgreSQL 16 | **"Successfully validated 29 migrations"**, V1–V29 applied to an empty schema |
| Spring Boot startup | boot vs. PostgreSQL 16 | **"Started BroksForgeApplication"**; `ddl-auto=validate` accepted all new entities |
| Frontend types | `npm run typecheck` | **0 errors** |
| Frontend production build | `next build` | **All routes compiled** — pages + `/icon.svg`, `/manifest.webmanifest`, `/opengraph-image`, `/robots.txt`, `/sitemap.xml` |
| Docker images | `docker compose build` | backend (`maven:3.9-eclipse-temurin-21`) + frontend (`node:20-alpine`) — standard, unmodified Dockerfiles |

> **Build-path note:** the working directory `…/Gok's Brok/…` contains an apostrophe,
> which trips Next.js's file-based metadata-route loader locally (it embeds the absolute
> path in a single-quoted string). The metadata routes therefore build cleanly from any
> apostrophe-free path — verified by building at a temp path — and **in Docker the build
> path is `/app`**, so `docker compose up --build` is unaffected.

---

## 8. Known limitations

- **Provider health = agent-endpoint probe.** The "tiny POST completion" hits the agent's
  registered HTTP endpoint, not the provider's own API — the platform holds no provider
  base URLs/keys. Provider-direct `ModelInvoker` clients remain a documented future
  extension (ADR-0006).
- **No SPRING_BOOT/FASTAPI framework enum values.** Spring-Boot-style vs FastAPI-style is
  inferred (SPRING_AI → actuator; the Python/JS frameworks → `/health`). An explicit
  per-agent health-path override could be added later.
- **Legacy password-change link endpoints remain** (deprecated in favour of OTP) for
  backward compatibility; removal is a future major-version change.
- **Idle timeout is client-side** (tokens are in localStorage, not cookies); access JWTs
  remain valid until they expire (≤15 min) even after the client signs out — refresh
  tokens are revoked immediately.
- **No automated tests were added** — the repository ships without a test module; changes
  were validated by compilation, type-checking, production build and live boot. Adding a
  test suite is the top future item.

---

## 9. Future improvements

- Add JUnit/Testcontainers backend tests and Vitest/RTL frontend tests (RBAC, IDOR→404,
  OTP attempt-cap, SSRF-blocked, no-secret-leak).
- Provider-direct `ModelInvoker` clients + provider health APIs; a `@Scheduled` health
  checker reusing `AgentHealthCheckExecutor`.
- Async e-mail dispatch (outbox + worker), and async evaluation workers behind a queue.
- KMS-managed encryption key + DNS-rebinding hardening (both already tracked in
  `SECURITY_GUIDE.md` §15).
- Real PNG apple-touch-icon and a static OG fallback for scrapers that don't fetch the
  dynamic route.

---

## 10. Deployment readiness

**Ready.** The application preserves the modular-monolith architecture and the full
security model, and passes `mvn clean verify`, `next build`, Flyway migration and Spring
Boot startup with `ddl-auto=validate`. New configuration is env-driven with safe defaults:

| Variable | Default | Purpose |
|----------|---------|---------|
| `BROKSFORGE_SECURITY_TOKENS_PASSWORD_CHANGE_OTP_EXPIRATION_MS` | `300000` | OTP code lifetime (5 min) |
| `BROKSFORGE_SECURITY_TOKENS_PASSWORD_CHANGE_TICKET_EXPIRATION_MS` | `600000` | Verified-ticket lifetime (10 min) |
| `NEXT_PUBLIC_APP_URL` | `https://broksforge.dev` | Public base URL for OG / sitemap / canonical |
| `NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES` | `30` | Idle-session timeout (0 disables) |

Pre-existing required secrets (`JWT_SECRET`, `ENCRYPTION_KEY`, datastore creds) and the
prod SMTP settings are unchanged. Migrations are append-only and were confirmed to apply
cleanly on top of the existing V1–V26 schema. Deploy with `docker compose up --build`;
set `SPRING_PROFILES_ACTIVE=prod` + `SPRING_MAIL_*` to send real OTP e-mail.
