# Forge Experience Platform — Deployment Guide

**Deliverable 12.** How to deploy FXP over the platform. The defining property is that **application
services are stateless pure functions of the kernel log**, so the only stateful tier is the kernel
store.

## Topology

```
        clients (web · CLI · SDK)
                │  HTTPS
        ┌───────▼────────┐
        │  API gateway    │  TLS · authn (OIDC/JWT) · authz · rate limit · pagination · error model
        └───────┬────────┘
     ┌──────────┼───────────┬───────────┐
  Studio     Explorer     Review      Copilot        (stateless FXP services; scale horizontally)
     └──────────┴───────────┴───────────┘
                │  public platform APIs (in-process ForgeClient, or platform service)
        ┌───────▼────────┐
        │  Forge Kernel   │  → Postgres store (the single stateful tier)
        └────────────────┘
```

## Prerequisites
- JDK 21+. Build order (offline): `kernel` → `forge-knowledge` → `forge-fvcs` → `forge-fkge` →
  `forge-fxp` (`mvn -o install` in each).
- A Postgres instance for the durable kernel store (`kernel-store-postgres`), or in-memory for
  dev/test (`Kernels.inMemory()`).

## Kernel store
```
PostgresKernels.migrate(dataSource);                       // one-time schema migration
ForgeKernel kernel = PostgresKernels.open(dataSource, reproducers);
Repository repo = Repository.open(kernel, org, actor);
ForgeClient client = ForgeClient.open(repo, actor);
```
The store is the only component holding state; back it up and replicate it as the system of record. The
log is append-only and hash-chained — backups are trivially consistent (no in-flight mutation).

## Scaling
- **Stateless services:** run N replicas of each experience behind the gateway; any replica answers any
  request by folding the log. No sticky sessions, no shared app state.
- **Reads:** cache by `(query, asOf)` — always safe because answers are content-addressed and
  `asOf`-stamped; a cache never changes a result and a cold cache reproduces it.
- **Writes:** serialize per branch `Name` via the kernel's CAS repoint; a concurrent advance fails with
  `CAS_FAILURE` (surface as HTTP `409`) — the client retries on the new head.

## Configuration
- `FORGE_ORG` — the org id the handle is bound to (tenant).
- `FORGE_DB_URL` / credentials — never logged; kept in a secret store.
- `FORGE_COPILOT_MODEL` — the `LanguageModel` adapter class (default: `TemplateLanguageModel`).

## Rollout
Blue/green at the app tier carries no migration risk (services are stateless). Kernel store migrations
run once via `PostgresKernels.migrate` ahead of the app rollout. Roll back the app tier freely; the log
is immutable, so no app version can corrupt history.

## Multi-tenant
One kernel log per org (a kernel invariant) gives hard tenant isolation. Deploy one gateway; bind each
authenticated request's principal and org, and construct a per-request `ForgeClient` for that org.
