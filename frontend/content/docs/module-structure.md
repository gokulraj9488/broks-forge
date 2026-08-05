# Module Structure

## Backend

Every backend module lives under `com.broksforge.modules.<name>` and follows the same internal
shape:

```
   modules/<name>/
     web/              @RestController, request/response DTOs, validation
       dto/
     service/          business logic; the only place rules live
     domain/           JPA entities and enums
     repository/       Spring Data JPA interfaces
```

The rule: **controllers do no business logic, services do no HTTP, repositories do no rules.** A
controller resolves the actor, checks membership and delegates.

### The modules

| Module | Owns |
| --- | --- |
| `auth`, `user`, `organization`, `project` | Identity, tenancy, membership, roles |
| `agent`, `prompt`, `dataset`, `provider`, `model` | Artifacts and their versions |
| `evaluation` | Jobs, runs, results, profiles, execution |
| `benchmark`, `regression` | Variant comparison and baselines |
| `analytics`, `dashboard`, `report`, `search` | Aggregate reads |
| `advisor`, `debugger` | Earlier-phase advisory surfaces |
| `rootcause` | The failure classifier (`RootCauseEngine`) |
| `knowledge` | The knowledge-graph learning seam |
| `platform` | Registry, Forge Graph, Engineering Intelligence, Evolution, AI Git |
| `brok` | The Engineering Partner |
| `investigation` | The Root Cause Explorer |
| `apikey`, `system` | Programmatic access, health |

### Dependency direction

Modules depend **downward through published services**, never on each other's repositories.

```
   investigation ──┬──► brok        (record snapshot, precedent, vocabulary)
                   ├──► rootcause   (the failure classifier)
                   ├──► platform    (intelligence, evolution, AI Git)
                   └──► evaluation  (jobs, runs, tallies)

   brok ───────────┬──► platform
                   └──► evaluation

   platform ──────────► artifact modules (agent, prompt, dataset, provider)
```

`investigation` never touches an `EvaluationRunRepository`; it calls `EvaluationService`. This is
what keeps tenant scoping in one place per entity rather than scattered.

### Reasoning modules own no data

`brok` and `investigation` have **no repository and no entity**. If a reasoning module needs a new
table, that is a signal the design is wrong — the object should be derived, or it belongs to the
module that owns the underlying record.

## Frontend

```
   src/app/
     (dashboard)/            the authenticated product
       brok/                 the Brok workspace
       registry/  knowledge/  insights/  analytics/  …
       organizations/[orgId]/projects/[projectId]/
         agents/[agentId]/    prompts/[promptId]/
         datasets/[datasetId]/
         evaluations/[jobId]/            and /investigate
     (auth)/                 login, register, password flows
     docs/                   this documentation
     page.tsx                the public landing page

   src/components/
     brok/                   workspace, answer, refs, investigation trace
     investigation/          workspace, timeline, causal chain
     platform/               forge graph, execution graph, intelligence,
                             evolution, verdict, evaluation pipeline
     landing/                the public site
     ui/                     design-system primitives
     layout/                 shell, navigation, header

   src/lib/
     api/                    typed REST clients, one per module
     hooks/                  React Query hooks, one per client
     verdict.ts              evaluative vocabulary (warm hues)
     substrate.ts            structural identity (cool hues)
     brok-actions.ts         action kind → real route
     artifact-links.ts       artifact → its workspace route
     docs.ts                 the documentation registry
```

### Frontend rules

**One API client per backend module**, in `lib/api/`, with types mirroring the DTOs.

**One hook module per client**, in `lib/hooks/`, owning query keys and cache policy.

**Routing lives in the client.** The API returns action *kinds*, never URLs;
`brok-actions.ts` is the single place that maps a kind to a route. A destination that cannot be
resolved renders without a link rather than pointing somewhere that does not exist.

**The design language is centralised.** `verdict.ts` owns how things are going; `substrate.ts` owns
what things are. The two palettes are never mixed — colour means one or the other, never both.

**Components are shared across surfaces, not copied.** `VerdictBanner`, `BrokRefGroup`,
`EpistemicMark` and `ForgeGraph` render in Brok, the Root Cause Explorer, artifact pages and briefs.
That is why those surfaces cannot visually drift apart.

## Adding a module

1. Create `modules/<name>/{web,service,domain,repository}`.
2. Scope every entity to `organizationId` (and `projectId` where it applies).
3. Enforce membership in the controller **and** re-scope in the service.
4. Write a Flyway migration; never modify an applied one.
5. Add an integration test against real PostgreSQL via Testcontainers.
6. Add the API client and hooks on the frontend.
7. **Check the constitution first:** does this belong in an existing module? Evolving beats adding.

See also: [Architecture Overview](/docs/architecture) ·
[Engineering Principles](/docs/engineering-principles) · [Extension Points](/docs/extension-points)
