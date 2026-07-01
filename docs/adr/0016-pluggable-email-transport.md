# 16. Pluggable e-mail transport (console for dev, SMTP for production)

- Status: Accepted
- Date: 2026-07-01

## Context

The platform sends transactional e-mail — **account verification** and **password
reset** — whose links carry one-time, expiring tokens. Two needs are in tension:

- **Local development / CI must work with zero setup**: no SMTP server, no e-mail
  account, no API keys, fully offline. A contributor must be able to register and
  verify an account immediately.
- **A public production deployment must send real e-mail** to real inboxes over SMTP,
  with credentials supplied by the environment (never hardcoded).

The authentication flow (token generation, hashing, persistence, expiry, one-time
use, resend) already exists and must not change. The transport must be swappable
**without the authentication code knowing which one is active.**

## Alternatives considered

- **One implementation, SMTP always.** Simple, but breaks the zero-config developer
  experience and CI (every contributor would need an SMTP account), and risks sending
  real e-mail from tests.
- **A runtime `if (prod) smtp else log`** inside a single service. Couples both
  transports and their dependencies into one class, and drags SMTP configuration into
  every environment. A code smell, not a seam.
- **`@ConditionalOnMissingBean` on the logging service** so a production bean "auto
  overrides" it. This is exactly the misuse that broke startup earlier: that condition
  is only reliable on **auto-configuration** classes, not component-scanned `@Service`s.
  Rejected.
- **A third-party provider SDK (SendGrid/SES/Resend/Postmark).** Vendor lock-in and an
  API-key dependency for V1. Deferred — the SMTP abstraction can reach any of them, and
  a provider-SDK implementation can be added later behind the same interface.

## Decision

Keep the existing **`EmailService` interface** as the single port, and provide **two
implementations selected by Spring profile**:

1. **`LoggingEmailService`** — annotated **`@Profile("!prod")`**. Writes the
   verification/reset messages (with the clickable URL) to the application log. Zero
   config, no SMTP, no account, no API keys, fully offline. The default for `dev`,
   `docker`, `test` and CI.
2. **`SmtpEmailService`** — annotated **`@Profile("prod")`**. Sends real branded e-mail
   via Spring's **`JavaMailSender`** as a **`multipart/alternative`** message (HTML +
   plain-text fallback). All SMTP host/port/credentials come from **`spring.mail.*`
   environment variables**; nothing is hardcoded.
3. **Exactly one `EmailService` bean exists per profile**, so injection is unambiguous
   and **`AuthService` depends only on the interface** — it never knows which transport
   is active (constructor parameter type is `EmailService`).
4. A shared, pure **`EmailContentFactory`** builds the branded HTML + plain-text bodies
   (inline-styled, injection-safe via `HtmlUtils.htmlEscape`), so content is identical
   regardless of transport and is unit-testable without sending anything.
5. **Production activation is a deployment decision**: set `SPRING_PROFILES_ACTIVE=prod`
   and supply `SPRING_MAIL_*`. Without SMTP configured, `prod` **fails fast** (no
   `JavaMailSender`) — a misconfigured production is a hard error, not a silent drop.

## Consequences

**Positive**
- **Zero-config local development and CI** (console transport) *and* **real e-mail in
  production** (SMTP) from the same codebase, chosen purely by profile/configuration.
- `AuthService` and the whole auth flow are **untouched** — the swap is invisible to
  business logic (the interface never changed).
- Branded, accessible e-mail with a **plain-text fallback**; content is centralised and
  testable.
- No new vendor dependency or API key for V1; credentials are **env-only**.

**Negative / trade-offs**
- E-mail is sent **synchronously** within the request transaction, so a slow/failing
  SMTP server surfaces as a request error (fail-closed). Acceptable for V1; async
  dispatch with retry is a documented future enhancement.
- The `prod` profile **requires** SMTP configuration to start — deliberate (you cannot
  run production without a way to verify accounts), but it means `prod` is not a
  "no-config" profile.

## Future impact

- A **provider-SDK implementation** (SendGrid/SES/Postmark) is a new `@Profile`-gated
  (or `@ConditionalOnProperty`) `EmailService` — no auth changes.
- **Asynchronous, retrying delivery** (outbox + worker) slots behind the same interface,
  reusing the async seam pattern from the evaluation executor.
- Additional templates (welcome, security alerts) are new `EmailContentFactory` methods.
