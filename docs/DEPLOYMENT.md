# Deployment Guide — Railway (backend) + Vercel (frontend)

This guide walks through deploying Brok's Forge to production: the Spring Boot API on
[Railway](https://railway.app) and the Next.js frontend on [Vercel](https://vercel.com). It
assumes you already have a GitHub remote for this repository — both platforms deploy by
connecting to a Git repo and building on push.

## Architecture

```
Vercel (frontend, Next.js)  --HTTPS-->  Railway (backend, Spring Boot)
                                              |
                                              +--> Railway Postgres (managed)
                                              +--> Railway Redis (managed)
```

The backend is stateless (JWT auth, no server-side sessions) so it scales horizontally on
Railway without sticky sessions. Redis is required for auth rate-limiting and idempotency —
without it, rate-limiting fails open (logs a warning, does not block requests) but every other
feature works normally.

## 1. Provision the database and cache (Railway)

1. Create a new Railway project.
2. Add a **PostgreSQL** plugin — Railway provisions it and exposes `DATABASE_URL`,
   `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` as variables you can reference.
3. Add a **Redis** plugin the same way — exposes `REDIS_URL` / `REDISHOST` / `REDISPORT` /
   `REDISPASSWORD`.
4. Flyway migrations run automatically on backend startup (`spring.flyway.enabled: true`,
   `baseline-on-migrate: true`) — no manual migration step is needed.

## 2. Deploy the backend (Railway)

1. Add a new Railway service from this GitHub repo, root directory `backend/` (Railway
   auto-detects the `Dockerfile` and builds it — no buildpack config needed).
2. Set the service's environment variables (Railway → Variables tab). At minimum:

   | Variable | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
   | `SPRING_DATASOURCE_USERNAME` | `${{Postgres.PGUSER}}` |
   | `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `SPRING_DATA_REDIS_HOST` | `${{Redis.REDISHOST}}` |
   | `SPRING_DATA_REDIS_PORT` | `${{Redis.REDISPORT}}` |
   | `SPRING_DATA_REDIS_PASSWORD` | `${{Redis.REDISPASSWORD}}` |
   | `BROKSFORGE_SECURITY_JWT_SECRET` | output of `openssl rand -base64 48` |
   | `BROKSFORGE_SECURITY_ENCRYPTION_KEY` | output of `openssl rand -base64 32` |
   | `BROKSFORGE_SECURITY_CORS_ALLOWED_ORIGINS` | your Vercel frontend URL, e.g. `https://broksforge.vercel.app` |
   | `BROKSFORGE_APP_PUBLIC_URL` | same Vercel URL (used to build links in emails) |
   | `SPRING_MAIL_HOST` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | your SMTP provider (required — the `prod` profile fails fast without `SPRING_MAIL_HOST`) |
   | `BROKSFORGE_MAIL_FROM_ADDRESS` | e.g. `no-reply@yourdomain.com` |

   Leave `AGENT_HEALTH_ALLOW_PRIVATE_TARGETS` / `MODEL_ALLOW_PRIVATE_TARGETS` unset (they
   default to `false`) — the SSRF guard must stay locked down in production. Only native
   Ollama providers bypass it, and only for `localhost`/`127.0.0.1`/`host.docker.internal`.
3. Railway injects `PORT` automatically; the backend now reads `server.port: ${PORT:8080}`
   (see `application.yml`), so no port configuration is needed on your end.
4. Deploy. Watch the build logs, then confirm health:
   - `GET https://<your-railway-domain>/actuator/health` → `{"status":"UP"}`
   - `GET https://<your-railway-domain>/actuator/health/readiness` includes a `db` check —
     confirms Postgres connectivity specifically.

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
| `BROKSFORGE_SECURITY_JWT_SECRET` | `openssl rand -base64 48` |
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
