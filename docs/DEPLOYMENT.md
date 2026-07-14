# Deployment Guide — AWS EC2 (backend) + Vercel (frontend)

**Primary production target.** The Spring Boot API runs on a self-managed AWS EC2 instance
(Amazon Linux 2023, Docker Compose) behind Nginx with Let's Encrypt TLS; the Next.js frontend
runs on Vercel. Railway is no longer used — see [Archived: Railway
deployment](#archived-railway-deployment) at the bottom of this document if you need the old
walkthrough for reference.

For the one-time server bring-up (EC2 provisioning, security group, Docker install, TLS
bootstrap, backups, monitoring), see **[AWS_EC2_SETUP.md](./AWS_EC2_SETUP.md)**. This document
covers the day-to-day deployment flow, environment variables, domain/DNS architecture, GitHub
Actions/Secrets, and the FAQs (Redis, SMTP) that apply regardless of host.

## Architecture

```
Vercel (frontend, Next.js)  --HTTPS-->  api.broksforge.gokul.quest
                                              │
                                              ▼
                                        AWS EC2 (Nginx + TLS)
                                              │
                                              ▼
                                    Spring Boot API (Docker container)
                                              │
                                    ┌─────────┴─────────┐
                                    ▼                   ▼
                            PostgreSQL (container)  Redis (container, optional)
```

The backend is stateless (JWT auth, no server-side sessions), so it scales horizontally
without sticky sessions if you ever run more than one backend container behind Nginx. Neither
PostgreSQL nor Redis is exposed to the host or the public internet — see
[AWS_EC2_SETUP.md → Security](./AWS_EC2_SETUP.md#security).

## Domain architecture

This deployment deliberately does **not** use the root domain — `gokul.quest` remains the
portfolio site, untouched.

| Subdomain | Serves | Hosted on |
|---|---|---|
| `gokul.quest` | Portfolio (unrelated to Brok's Forge) | Unchanged |
| `broksforge.gokul.quest` | Frontend (Next.js) | Vercel |
| `api.broksforge.gokul.quest` | Backend API | AWS EC2 (this guide) |

### DNS records

Configure these at your DNS provider (wherever `gokul.quest` is managed):

| Type | Host | Value | Notes |
|---|---|---|---|
| CNAME | `broksforge` | `cname.vercel-dns.com` | Vercel's recommended record for a subdomain (add the custom domain in Vercel's project settings first — it tells you the exact target) |
| A | `api.broksforge` | `<EC2 Elastic IP>` | Must be an **Elastic IP** (see AWS_EC2_SETUP.md) — a non-elastic public IP changes on stop/start and silently breaks this record |

Both are subdomains of `gokul.quest`, so no change to the root domain's own records is needed.

### Vercel configuration

1. Project → Settings → Domains → add `broksforge.gokul.quest`.
2. Project → Settings → Environment Variables:

   | Variable | Value |
   |---|---|
   | `NEXT_PUBLIC_API_BASE_URL` | `https://api.broksforge.gokul.quest` |
   | `NEXT_PUBLIC_APP_URL` | `https://broksforge.gokul.quest` |
   | `NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES` | `30` (or your preference) |

3. Redeploy after setting these — `NEXT_PUBLIC_API_BASE_URL` is baked into the build (it drives
   the CSP `connect-src` in `next.config.mjs`), not read at runtime.

### CORS configuration

Set on the EC2 host's `.env` (see `.env.production.example`):

```
CORS_ALLOWED_ORIGINS=https://broksforge.gokul.quest
APP_PUBLIC_URL=https://broksforge.gokul.quest
```

This is already fully environment-driven in the codebase (`CorsConfig` / `CorsProperties`) —
no code change was needed to point it at this domain.

## Is Redis required?

No. Redis is used in exactly two places in this codebase, both traced directly from the
source — nothing else references it:

| Feature | Redis dependency | Can run without Redis? | Recommended fallback |
|---|---|---|---|
| Auth-endpoint rate limiting (`RateLimiterService` / `AuthRateLimitInterceptor`) — register/login/verify-email/forgot-password | Rate limiting only (fixed-window counter, distributed across replicas) | **Yes** | `NoOpRateLimiterService` — auto-selected when no `RedisConnectionFactory` bean exists; always allows the request. |
| `RedisConfig.redisTemplate` bean | None — declared for future caching/token-revocation use cases but **no `@Cacheable`, no cache manager, no other class currently reads or writes through it** | **Yes** | The bean simply isn't created (`@ConditionalOnBean(RedisConnectionFactory.class)`) when Redis is absent. |

Nothing in the evaluation engine, agent registry, providers, datasets, benchmarks, or advisor
modules touches Redis — the background evaluation runner uses an in-process worker pool
(`broksforge.evaluation.worker-concurrency`), not a Redis-backed queue. Authentication is pure
JWT (stateless, no server-side session store).

`docker-compose.prod.yml` runs Redis by default (with a required password — see
`.env.production.example`) since it costs little on a `t3.small` and keeps rate limiting
distributed/tested exactly like local Docker Compose. To run without it instead, remove the
`redis` service from `docker-compose.prod.yml` and set
`SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration`
on the `backend` service — the app detects the missing `RedisConnectionFactory` bean and wires
`NoOpRateLimiterService` automatically.

## Is e-mail (SMTP) required?

No — e-mail is optional in every profile, including `prod`. `EmailServiceConfig` wires
exactly one `EmailService` bean: `SmtpEmailService` if `spring.mail.host` is set (via the
`SPRING_MAIL_HOST` env var), otherwise `LoggingEmailService`, which prints
verification/reset/notification links straight to the container logs instead of sending real
e-mail. Every auth flow (registration, password reset, password change) still completes
end-to-end without SMTP configured — you just read the link from
`docker compose -f docker-compose.prod.yml logs backend` instead of an inbox.

Set `SPRING_MAIL_HOST` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` in `.env` whenever you
want real transactional e-mail; leave them unset to run without SMTP.

## Deploying (after the one-time setup)

Once [AWS_EC2_SETUP.md](./AWS_EC2_SETUP.md) is complete, every deployment is:

```bash
git push origin main
```

`.github/workflows/deploy-production.yml` does the rest: SSH into EC2 → `git pull` → `docker
compose pull` (base images) → `docker compose build` (backend) → `docker compose up -d` →
prune dangling images → health check (container-internal, then the public HTTPS endpoint).
No manual deployment step. See [GitHub Actions](#github-actions) below for the workflow itself
and [GitHub Secrets](#github-secrets) for what it needs configured.

To deploy manually from the EC2 host instead (e.g. while debugging):

```bash
cd /opt/apps/broks-forge
git pull
docker compose -f docker-compose.prod.yml pull --ignore-pull-failures
docker compose -f docker-compose.prod.yml build backend
docker compose -f docker-compose.prod.yml up -d
docker image prune -f
```

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
   from outermost to innermost, then logs the root cause's own stack trace separately —
   immune to a log viewer's tail/scrollback limit truncating a long combined trace. Credentials
   are redacted the same way as the startup banner.
2. If that still isn't enough context, set `DEBUG=true` in `.env`, redeploy, and check the
   condition evaluation report. It's off by default and has no effect on runtime behavior.
3. `docker compose -f docker-compose.prod.yml logs backend` for the raw container log;
   `docker compose -f docker-compose.prod.yml logs nginx` if Nginx itself won't start (usually
   a missing/misconfigured certificate — see AWS_EC2_SETUP.md's TLS bootstrap step).

Common root causes: the `.env` datasource credentials are wrong, the EC2 security group
doesn't allow the traffic you expect, or a Flyway migration checksum/schema mismatch against a
database that already has partial state from a previous deploy attempt.

## GitHub Actions

`.github/workflows/deploy-production.yml` triggers on every push to `main` (guarded to the
canonical repo owner, so a fork's copy of this workflow can never deploy against your
infrastructure) and:

1. Writes the deploy SSH key to a temp file, adds the EC2 host to `known_hosts`.
2. SSHes in and runs, on the EC2 host: `git fetch`/`reset --hard origin/main`, `docker compose
   pull --ignore-pull-failures` (pulls Postgres/Redis/Nginx/Certbot's upstream images; the
   backend has no registry image, so its pull attempt fails harmlessly and is ignored — it's
   built fresh in the next step), `docker compose build backend`, `docker compose up -d`,
   `docker image prune -f`.
3. Polls the backend's own health check from inside the SSH session, then re-verifies via the
   public HTTPS endpoint (`https://api.broksforge.gokul.quest/actuator/health`) — confirming
   not just that the container is healthy but that Nginx/TLS/DNS are actually routing to it.
4. Deletes the temporary SSH key (`if: always()`, so it runs even if a prior step failed).

`docker compose up -d` only recreates containers whose config actually changed — Postgres and
Redis keep running untouched across a deploy that only touched backend code.

## GitHub Secrets

| Secret | Used for |
|---|---|
| `EC2_HOST` | The EC2 instance's public hostname/Elastic IP — where the deploy workflow SSHes to. |
| `EC2_USER` | The SSH user on the EC2 host (e.g. `ec2-user` for Amazon Linux). |
| `EC2_SSH_KEY` | The **private** half of a dedicated deploy keypair (see AWS_EC2_SETUP.md) — never your personal key. Its public half must be in that user's `~/.ssh/authorized_keys` on the EC2 host. |
| `EC2_DEPLOY_PATH` | *(Optional)* Overrides the repo path on the EC2 host if it isn't `/opt/apps/broks-forge`. |

**Deliberately NOT duplicated into GitHub Secrets:** `JWT_SECRET`, `ENCRYPTION_KEY`,
`POSTGRES_PASSWORD`, `REDIS_PASSWORD`, SMTP credentials, etc. Those live **only** in `.env` on
the EC2 host (see `.env.production.example`) — the deploy workflow never reads or transmits
them; it only SSHes in and runs `docker compose up -d`, which reads `.env` directly off the
server's disk. This keeps the blast radius of a compromised GitHub Actions run limited to SSH
access to the deploy path, rather than every application secret.

If you ever need to *rotate* an application secret, that's a direct edit of `.env` on the EC2
host followed by `docker compose -f docker-compose.prod.yml up -d` (recreates only the
`backend` service, since only its environment changed) — not a GitHub Actions concern at all.

## Post-deploy verification checklist

- [ ] Backend healthy: `docker compose -f docker-compose.prod.yml ps` shows `backend` as
      `(healthy)`.
- [ ] PostgreSQL healthy: same `ps` output, `postgres` as `(healthy)`.
- [ ] Docker healthy: `docker compose -f docker-compose.prod.yml ps` shows no `(unhealthy)` or
      restarting containers.
- [ ] Nginx healthy: `curl -s http://<EC2 IP>/healthz` (or from the host itself) → `ok`.
- [ ] HTTPS working: `curl -sI https://api.broksforge.gokul.quest/actuator/health` → `HTTP/2
      200`, valid certificate (no `-k` needed).
- [ ] Domain reachable: `curl -s https://api.broksforge.gokul.quest/actuator/health` →
      `{"status":"UP"}` from a machine that is **not** the EC2 host itself (proves DNS + the
      security group's port 443 rule, not just loopback).
- [ ] GitHub Actions successful: the `Deploy to Production (AWS EC2)` workflow run is green.
- [ ] Frontend connected: `https://broksforge.gokul.quest` loads and
      `POST /api/v1/auth/register` succeeds (confirms CORS specifically — a misconfigured
      `CORS_ALLOWED_ORIGINS` fails silently as a browser CORS error, not a 4xx/5xx, so check
      the browser console).
- [ ] API reachable from the frontend's actual origin (not just `curl`) — register a user,
      confirm the verification link/e-mail arrives (console log if SMTP isn't configured, real
      e-mail if it is).
- [ ] Health endpoint responding with `show-details: never` (prod profile) — hitting
      `/actuator/health` unauthenticated returns only `{"status":"UP"}`, no component
      breakdown, no database version/connection details.
- [ ] Create an organization → project → provider → agent → dataset → evaluation job, confirm
      it completes (exercises the full write path against the real Postgres instance).

## Rolling back

`git reset --hard <previous-commit>` on the EC2 host, then re-run the manual deploy sequence
above (or `git revert` on `main` and let GitHub Actions redeploy). Since Flyway migrations are
additive and forward-only in this codebase, rolling back the **application** is safe at any
point; rolling back the **database** (if a migration needs reverting) is a manual operation —
restore from `scripts/restore-postgres.sh` if needed, since Flyway does not auto-generate
down-migrations.

## Local Docker Compose stays the reference environment

`docker-compose.yml` (unchanged by this production pass) remains the source of truth for local
development — it still runs backend + frontend + Postgres + Redis together with the `docker`
profile. `docker-compose.prod.yml` is a **separate, standalone** file for the EC2 host only; it
does not extend or merge with `docker-compose.yml`, so nothing about local development changed.

---

## Archived: Railway deployment

Brok's Forge previously ran on Railway (backend) + Vercel (frontend). Railway is no longer
used — AWS EC2 (above) is the permanent production target. This section is kept only as a
historical/alternative reference; none of it is required or maintained going forward.

<details>
<summary>Expand for the archived Railway walkthrough</summary>

### Provision the database (and optionally Redis) on Railway

1. Create a new Railway project.
2. Add a **PostgreSQL** plugin — Railway provisions it and exposes `DATABASE_URL`, `PGHOST`,
   `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` as variables you can reference.
3. Redis is optional (see [Is Redis required?](#is-redis-required) above) — add a **Redis**
   plugin only if you want it.
4. Flyway migrations run automatically on backend startup — no manual migration step needed.

### Deploy the backend (Railway)

1. Add a new Railway service from this GitHub repo, root directory `backend/` (Railway
   auto-detects the `Dockerfile`).
2. Set the service's environment variables (Railway → Variables tab):

   | Variable | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` — do not leave unset (the base profile otherwise falls back to `dev` + a `localhost` datasource default). |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` — required, no default in the `prod` profile. |
   | `SPRING_DATASOURCE_USERNAME` | `${{Postgres.PGUSER}}` |
   | `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `SPRING_DATA_REDIS_HOST` / `PORT` / `PASSWORD` | Only if you provisioned Redis: `${{Redis.REDISHOST}}` / `${{Redis.REDISPORT}}` / `${{Redis.REDISPASSWORD}}`. |
   | `SPRING_AUTOCONFIGURE_EXCLUDE` | Only if you did NOT provision Redis: `org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration`. |
   | `BROKSFORGE_SECURITY_JWT_SECRET` | `openssl rand -base64 48` |
   | `BROKSFORGE_SECURITY_ENCRYPTION_KEY` | `openssl rand -base64 32` |
   | `BROKSFORGE_SECURITY_CORS_ALLOWED_ORIGINS` | your Vercel frontend URL |
   | `BROKSFORGE_APP_PUBLIC_URL` | same Vercel URL |
   | `SPRING_MAIL_HOST` / `USERNAME` / `PASSWORD` | Optional — see [Is e-mail (SMTP) required?](#is-e-mail-smtp-required). |
   | `BROKSFORGE_MAIL_FROM_ADDRESS` | e.g. `no-reply@yourdomain.com` |

   Leave `AGENT_HEALTH_ALLOW_PRIVATE_TARGETS` / `MODEL_ALLOW_PRIVATE_TARGETS` unset (default
   `false`) — the SSRF guard must stay locked down in production.
3. Railway injects `PORT` automatically; the backend reads `server.port: ${PORT:8080}`.
4. Deploy, then confirm `GET https://<railway-domain>/actuator/health` → `{"status":"UP"}`.

### Secrets checklist (Railway)

| Secret | How to generate |
|---|---|
| `BROKSFORGE_SECURITY_JWT_SECRET` | `openssl rand -base64 48` |
| `BROKSFORGE_SECURITY_ENCRYPTION_KEY` | `openssl rand -base64 32` |
| `SPRING_MAIL_PASSWORD` | your SMTP provider's app password / API key |
| Database/Redis passwords | auto-generated by Railway's managed plugins |

### Rolling back (Railway)

Railway → Deployments tab → "Redeploy" an earlier build; Vercel → Deployments → "Promote to
Production" on an earlier build.

</details>
