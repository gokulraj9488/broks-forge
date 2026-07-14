# Deployment Guide — Railway (backend) + Vercel (frontend)

This guide walks through deploying Brok's Forge to production: the Spring Boot API on
[Railway](https://railway.app) and the Next.js frontend on [Vercel](https://vercel.com). It
assumes you already have a GitHub remote for this repository — both platforms deploy by
connecting to a Git repo and building on push.

## Architecture

```
Vercel (frontend, Next.js)  --HTTPS-->  Railway (backend, Spring Boot)
                                              |
                                              +--> Railway Postgres (required)
                                              +--> Railway Redis (optional)
```

The backend is stateless (JWT auth, no server-side sessions) so it scales horizontally on
Railway without sticky sessions. **Redis is optional** — see [Is Redis
required?](#is-redis-required) below. PostgreSQL is the only mandatory managed dependency.

## Is Redis required?

No. Redis is used in exactly two places in this codebase, both traced directly from the
source — nothing else references it:

| Feature | Redis dependency | Can run without Redis? | Recommended fallback |
|---|---|---|---|
| Auth-endpoint rate limiting (`RateLimiterService` / `AuthRateLimitInterceptor`) — register/login/verify-email/forgot-password | Rate limiting only (fixed-window counter, distributed across replicas) | **Yes** | `NoOpRateLimiterService` — auto-selected when no `RedisConnectionFactory` bean exists; always allows the request. Single-instance deployments (Railway's default) don't need distribution anyway. |
| `RedisConfig.redisTemplate` bean | None — declared for future caching/token-revocation use cases but **no `@Cacheable`, no cache manager, no other class currently reads or writes through it** | **Yes** | The bean simply isn't created (`@ConditionalOnBean(RedisConnectionFactory.class)`) when Redis is absent. |

Nothing in the evaluation engine, agent registry, providers, datasets, benchmarks, or advisor
modules touches Redis — the background evaluation runner uses an in-process worker pool
(`broksforge.evaluation.worker-concurrency`), not a Redis-backed queue. Authentication is pure
JWT (stateless, no server-side session store).

**To run without Redis on Railway:** don't add the Redis plugin, and set
`SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration`.
The app detects the missing `RedisConnectionFactory` bean and wires `NoOpRateLimiterService`
automatically — no other code path changes, and `/actuator/health` no longer reports a Redis
component (rather than misleadingly showing `DOWN` for a dependency you never provisioned).

Local Docker Compose is unaffected either way: `docker-compose.yml` still provisions and wires
a real `redis` service by default, so local rate limiting stays distributed/tested exactly as
before.

## Is e-mail (SMTP) required?

No — e-mail is optional in every profile, including `prod`. `EmailServiceConfig` wires
exactly one `EmailService` bean: `SmtpEmailService` if `spring.mail.host` is set (via the
`SPRING_MAIL_HOST` env var, any profile), otherwise `LoggingEmailService` — which prints
verification/reset/notification links straight to the console instead of sending real
e-mail. Every auth flow (registration, password reset, password change) still completes
end-to-end without SMTP configured; you just read the link from the Railway logs instead of
an inbox.

**Incident this fixes:** an earlier version of `application-prod.yml` declared
`spring.mail.host: ${SPRING_MAIL_HOST}` with no default. Spring Boot's
`MailSenderAutoConfiguration` inspects `spring.mail.host` at startup *before* creating any
bean, to decide whether to activate; with the env var unset, resolving that placeholder
threw `IllegalArgumentException: Could not resolve placeholder 'SPRING_MAIL_HOST'` **during
condition evaluation itself** — surfaced as the fatal `"Error processing condition on
org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration"` startup failure.
It was a hard crash before a single mail bean was ever created, not a failed SMTP
connection. The fix removes that no-default placeholder entirely (Spring Boot's own
relaxed env-var binding already maps `SPRING_MAIL_HOST` → `spring.mail.host` without any
`yml` declaration when you do set it) and makes `SmtpEmailService` activation conditional
on the property actually being present.

Set `SPRING_MAIL_HOST` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` whenever you want
real transactional e-mail; leave them unset to run without SMTP.

## 1. Provision the database (and optionally Redis) on Railway

1. Create a new Railway project.
2. Add a **PostgreSQL** plugin — Railway provisions it and exposes `DATABASE_URL`,
   `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` as variables you can reference.
   This is the only datastore Brok's Forge cannot run without.
3. **Redis is optional.** Add a **Redis** plugin only if you want distributed auth-endpoint
   rate limiting (holds the limit across horizontally-scaled replicas). If you skip it, set
   `SPRING_AUTOCONFIGURE_EXCLUDE` (see the variables table below) and the app falls back to a
   no-op rate limiter automatically — every other feature is unaffected. See [Is Redis
   required?](#is-redis-required).
4. Flyway migrations run automatically on backend startup (`spring.flyway.enabled: true`,
   `baseline-on-migrate: true`) — no manual migration step is needed.

## 2. Deploy the backend (Railway)

1. Add a new Railway service from this GitHub repo, root directory `backend/` (Railway
   auto-detects the `Dockerfile` and builds it — no buildpack config needed).
2. Set the service's environment variables (Railway → Variables tab). At minimum:

   | Variable | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` — **do not leave this unset.** With no profile active, Spring Boot falls back to the `dev` profile's defaults, and the base `application.yml` datasource falls back to `localhost:5432` when `SPRING_DATASOURCE_URL` isn't resolvable — the exact combination that causes Flyway to try connecting to `localhost` and crash on Railway. |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` — **required, no default in the `prod` profile.** If this (or the two below) is missing, the app now fails fast at startup with a clear "could not resolve placeholder" error instead of silently trying `localhost`. |
   | `SPRING_DATASOURCE_USERNAME` | `${{Postgres.PGUSER}}` |
   | `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` / `SPRING_DATA_REDIS_PASSWORD` | Only if you provisioned the Redis plugin: `${{Redis.REDISHOST}}` / `${{Redis.REDISPORT}}` / `${{Redis.REDISPASSWORD}}`. |
   | `SPRING_AUTOCONFIGURE_EXCLUDE` | **Only set this if you did NOT provision Redis:** `org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration` — this removes the Redis connection factory entirely, and the app automatically wires a no-op rate limiter in its place (see [Is Redis required?](#is-redis-required)). Leave unset if you did add the Redis plugin. |
   | `BROKSFORGE_SECURITY_JWT_SECRET` | output of `openssl rand -base64 48` |
   | `BROKSFORGE_SECURITY_ENCRYPTION_KEY` | output of `openssl rand -base64 32` |
   | `BROKSFORGE_SECURITY_CORS_ALLOWED_ORIGINS` | your Vercel frontend URL, e.g. `https://broksforge.vercel.app` |
   | `BROKSFORGE_APP_PUBLIC_URL` | same Vercel URL (used to build links in emails) |
   | `SPRING_MAIL_HOST` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | **Optional** — your SMTP provider. See [Is e-mail (SMTP) required?](#is-e-mail-smtp-required). |
   | `BROKSFORGE_MAIL_FROM_ADDRESS` | e.g. `no-reply@yourdomain.com` (only used if SMTP is configured) |
   | `DEBUG` | **Optional, troubleshooting only** — `true` turns on Spring Boot's condition evaluation report for that run (see [Troubleshooting a startup failure](#troubleshooting-a-startup-failure)). Leave unset normally; no effect on runtime behavior either way. |

   Leave `AGENT_HEALTH_ALLOW_PRIVATE_TARGETS` / `MODEL_ALLOW_PRIVATE_TARGETS` unset (they
   default to `false`) — the SSRF guard must stay locked down in production. Only native
   Ollama providers bypass it, and only for `localhost`/`127.0.0.1`/`host.docker.internal`.
3. Railway injects `PORT` automatically; the backend now reads `server.port: ${PORT:8080}`
   (see `application.yml`), so no port configuration is needed on your end.
4. Deploy. Watch the build logs, then confirm health:
   - `GET https://<your-railway-domain>/actuator/health` → `{"status":"UP"}`
   - `GET https://<your-railway-domain>/actuator/health/readiness` includes a `db` check —
     confirms Postgres connectivity specifically.

### Troubleshooting a startup failure

Every boot prints a `=== Startup diagnostics ===` banner (active profile, the datasource URL
with credentials redacted, and the Flyway/JPA config in effect) **before** Spring attempts to
build the DataSource, run Flyway, or create the JPA `EntityManagerFactory`. If startup then
fails with something like:

```
Cannot resolve reference to bean 'jpaSharedEM_entityManagerFactory'
```

that is always a **downstream symptom** — it names whichever repository bean happened to be
instantiated first, not the actual cause. You no longer need to hunt for it manually:

1. Look for the `=== STARTUP FAILED — ROOT CAUSE ===` block. It always appears, prints the
   root cause's exception type + message on its own line, then the **full exception chain**
   from outermost to innermost (so you can see `entityManagerFactory` failed *because of*
   Flyway/HikariCP/Hibernate, not the other way round), then logs the root cause's own stack
   trace separately — so it's never the part buried under Spring's own (much longer) wrapper
   trace or cut off by a log viewer's tail/scrollback limit. Credentials are redacted the same
   way as the startup banner.
2. If that still isn't enough context (e.g. you need to see *why* a particular
   autoconfiguration did or didn't apply), set `DEBUG=true` as a Railway variable and redeploy
   — this turns on Spring Boot's condition evaluation report for that one run. It's off by
   default and has no effect on runtime behavior, only on startup logging, so it's safe to
   toggle on/off per deploy.

Common root causes: the datasource URL/credentials are wrong (compare against the banner's
redacted URL), Postgres requires SSL and the URL is missing `?sslmode=require`, or a Flyway
migration checksum/schema mismatch against a database that already has partial state from a
previous deploy attempt.

## 3. Deploy the frontend (Vercel)

1. Import the GitHub repo into Vercel, set the project root to `frontend/`.
2. Framework preset: Next.js (auto-detected). Build command and output are Vercel defaults —
   no changes needed; `next.config.mjs`'s `output: "standalone"` is harmless on Vercel (Vercel
   uses its own build output regardless).
3. Set environment variables (Vercel → Settings → Environment Variables):

   | Variable | Value |
   |---|---|
   | `NEXT_PUBLIC_API_BASE_URL` | your Railway backend URL, e.g. `https://broksforge-api.up.railway.app` |
   | `NEXT_PUBLIC_APP_URL` | your Vercel frontend URL |
   | `NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES` | `30` (or your preference) |

4. Deploy. The CSP in `next.config.mjs` already includes `connect-src 'self' <api origin>`
   computed from `NEXT_PUBLIC_API_BASE_URL` at build time — make sure the env var is set
   **before** the first production build, since it's baked in at build time, not read at
   runtime.

## 4. Post-deploy verification checklist

- [ ] `GET /actuator/health` on the Railway backend returns `UP`.
- [ ] Frontend loads and `POST /api/v1/auth/register` succeeds (confirms CORS is configured
      correctly — a misconfigured `BROKSFORGE_SECURITY_CORS_ALLOWED_ORIGINS` fails silently
      as a browser CORS error, not a 4xx/5xx, so check the browser console specifically).
- [ ] Register a user, confirm the verification email arrives (confirms SMTP config).
- [ ] Create an organization → project → provider → agent → dataset → evaluation job, confirm
      it completes (exercises the full write path against the real Postgres instance).
- [ ] Check Railway logs for `"log.level":"ERROR"` entries in the first few minutes of traffic.
- [ ] Confirm `management.endpoint.health.show-details: never` (prod profile) — hitting
      `/actuator/health` unauthenticated should return only `{"status":"UP"}`, no component
      breakdown, no database version/connection details.

## 5. Secrets checklist

Never commit real values for these — generate fresh secrets per environment:

| Secret | How to generate |
|---|---|
| `BROKSFORGE_SECURITY_JWT_SECRET` | `openssl rand -base64 48` — paste the output **exactly**: no surrounding quotes, no trailing newline/whitespace. Must decode (Base64) to at least 32 bytes; `JwtService` fails startup with a specific message (`Missing BROKSFORGE_SECURITY_JWT_SECRET` / `...is not valid Base64` / `...decodes to only N byte(s)`) if it doesn't — check that message first if the backend won't boot. |
| `BROKSFORGE_SECURITY_ENCRYPTION_KEY` | `openssl rand -base64 32` (rotating this invalidates all stored agent credentials — see `key-version` in `application.yml` for the rotation seam) |
| `SPRING_MAIL_PASSWORD` | your SMTP provider's app password / API key, not your account password |
| Database/Redis passwords | auto-generated by Railway's managed plugins |

## 6. Rolling back

Both platforms keep prior deployments: Railway → Deployments tab → "Redeploy" an earlier
build; Vercel → Deployments → "Promote to Production" on an earlier build. Since Flyway
migrations are additive and forward-only in this codebase, rolling back the **application**
is safe at any point; rolling back the **database** (if a migration needs reverting) is a
manual operation this guide does not cover — Flyway does not auto-generate down-migrations.

## 7. Local Docker Compose stays the reference environment

`docker-compose.yml` remains the source of truth for what environment variables the backend
and frontend read — this guide's tables mirror `.env.example`, which you should keep in sync
if you add new configuration.
