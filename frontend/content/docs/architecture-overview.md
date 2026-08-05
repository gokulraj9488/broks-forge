# Architecture Overview

Broks Forge is a Spring Boot API and a Next.js application over PostgreSQL and Redis. This page is
the public architecture reference; the full internal document is
[Master Architecture](/docs/master-architecture).

## The stack

| Tier | Technology |
| --- | --- |
| API | Java 21, Spring Boot 3.4, Maven |
| Database | PostgreSQL 16, migrated with Flyway |
| Cache / rate limiting | Redis |
| Web | Next.js 15 (App Router), React 19, TypeScript |
| Data fetching | TanStack Query v5, Axios |
| UI | Tailwind CSS, Radix primitives, lucide icons, @xyflow/react for graphs |
| Auth | JWT access + refresh tokens; API keys for programmatic access |
| Packaging | Docker Compose |

## Runtime shape

```
   ┌──────────────────────────────────────────────────────┐
   │  Next.js app (browser)                               │
   │  App Router · React Query · Tailwind                 │
   └───────────────────────┬──────────────────────────────┘
                           │ HTTPS, JWT bearer
   ┌───────────────────────▼──────────────────────────────┐
   │  Spring Boot API                                     │
   │                                                      │
   │   web/      controllers, DTOs, validation            │
   │   service/  business logic, derivation, reasoning    │
   │   domain/   JPA entities and enums                   │
   │   repository/ Spring Data JPA                        │
   └───────┬──────────────────────────────┬───────────────┘
           │                              │
   ┌───────▼────────┐            ┌────────▼────────┐
   │  PostgreSQL 16 │            │      Redis      │
   │  Flyway-managed│            │ cache, limits   │
   └────────────────┘            └─────────────────┘
                           │
                           │ outbound, during evaluations only
                           ▼
                  your agent endpoints
                  model provider APIs
```

Broks Forge is **not in your production request path**. It calls your agent's endpoint while an
evaluation runs, and never otherwise.

## Stored versus derived

The most important architectural decision in the platform.

```
   STORED (PostgreSQL, Flyway-migrated)
   ────────────────────────────────────
   users, organizations, memberships, projects
   agents · agent_versions
   prompts · prompt_versions
   datasets · dataset_versions · dataset_items
   providers, credentials (encrypted)
   evaluation_jobs · evaluation_runs · evaluation_results
   benchmarks, regression_checks, reports, api_keys

                    │
                    │  computed on read, never persisted
                    ▼

   DERIVED (no tables)
   ───────────────────
   Observation · Claim · Decision · Evidence · Knowledge
   Engineering Memory
   Forge Graph nodes and edges
   Evolution (lineage, dependents, impact)
   AI Git revision timelines
   Precedents
   Brok answers and briefs
   Investigations
```

**Nothing in the derived column has a table.** Every reasoning object is computed from stored rows
each time it is read.

This costs some CPU and buys three things that matter more: derived state can never drift from the
truth, there is no migration burden when the derivation improves, and a reasoning object cannot be
fabricated because there is no way to insert one.

See [Data Model](/docs/data-model) for detail.

## The layer rule

The [five layers](/docs/the-five-layers) are enforced as a dependency rule:

> **A layer may read everything beneath it and nothing above it, and no layer may duplicate a layer
> below it.**

Concretely:

- The Root Cause Explorer reuses the platform's existing failure classifier rather than writing a
  second one.
- Brok and the Root Cause Explorer share **one** precedent reading, so they cannot disagree about
  whether a failure has happened before.
- The Explorer reuses Brok's DTO vocabulary — verdicts, actions, references, recommendations — so the
  same frontend components render both surfaces.
- No reasoning application owns a repository.

## Multi-tenancy

Every row is scoped to an **organization**; most are additionally scoped to a **project**.

Two independent checks guard every request:

1. `OrganizationAccessService.requireMembership(organizationId, actorId)` at the controller.
2. The owning module's own service re-scopes the entity when it loads it.

A resource in another tenant is a **404**, never a 403 — the existence of a resource is itself
information.

## Platform V2 gating

The P7–P13 capabilities (Registry, Forge Graph, Engineering Intelligence, Evolution, AI Git, Brok,
Root Cause Explorer) sit behind a property:

```
broksforge.platform.v2.enabled = ${BROKSFORGE_PLATFORM_V2_ENABLED:true}
```

Controllers carry `@ConditionalOnProperty`, so the platform can be deployed with the V2 surface off
without removing code.

## Security

- **JWT** access and refresh tokens; the API fails fast at startup if `JWT_SECRET` is unset.
- **API keys** for programmatic access, hashed at rest.
- **Provider credentials encrypted** with `ENCRYPTION_KEY` (32 bytes), never returned by any read
  endpoint.
- **Deny-by-default** authorization — an endpoint without an explicit rule is inaccessible, and a
  test enforces this.
- **SSRF protection** on outbound agent calls.
- **Rate limiting** on authentication endpoints via Redis.

See [Security](/docs/security) for the full model.

## Frontend structure

```
   src/app/            App Router routes
     (dashboard)/      the authenticated product
     (auth)/           login, register, password flows
     docs/             this documentation
     page.tsx          the public landing page
   src/components/
     brok/             the Brok workspace
     investigation/    the Root Cause Explorer
     platform/         graphs, intelligence, evolution, verdict
     ui/               design-system primitives
   src/lib/
     api/              typed REST clients
     hooks/            React Query hooks
     verdict.ts        the verdict + epistemic vocabulary
     substrate.ts      structural identity for artifact types
```

The design language is centralised: `verdict.ts` owns evaluative state and `substrate.ts` owns
structural identity, and the two palettes are never mixed. Colour means either "what this is" or
"how it is going", never both.

## Testing

Integration tests run against a real PostgreSQL via **Testcontainers** — not an in-memory
substitute — so migrations, constraints and SQL behaviour are exercised as they will run in
production. The suite is currently **499 tests**.

See also: [Module Structure](/docs/module-structure) · [Data Model](/docs/data-model) ·
[REST API](/docs/rest-api) · [Engineering Principles](/docs/engineering-principles)
