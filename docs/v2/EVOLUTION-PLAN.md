# Broks Forge — Application Evolution Plan (V1 → one product powered by Platform V2)

**Planning only. No code.** Goal: evolve the existing Broks Forge application so Platform V2 becomes
its **internal architecture** — one product, one login, one org/project model, one backend, one
frontend, one database, one deployment. Platform V2 is an implementation detail the user never sees.
No frozen V2 module is modified; all bridging lives in the existing Spring Boot app.

---

## 1. Current Architecture Assessment

**Frontend** — `broks-forge-web` (Next.js, port 3000, `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`).
Route groups `(auth)` and `(dashboard)`; entities navigated as `org → project → {agents, prompts,
datasets, evaluations(+profiles), benchmarks}`; global pages: dashboard, providers, knowledge, advisor,
analytics, insights, settings, profile. Agent detail already has tabs (Overview, **Versions**, Advisor,
Health, Credentials, Settings, Streaming, Memory). Playwright e2e in `e2e/`.

**Backend** — `broks-forge-api` (Spring Boot 3.4.13, **Java 21**, port 8080). Web + Security (JWT,
refresh, email verify, password reset/OTP), JPA, **PostgreSQL 16**, **Flyway (40 migrations)**, **Redis
7**, Actuator, Spring AI, MapStruct, POI. Modules: auth, user (roles USER/ADMIN), organization
(+member), project, apikey, agent (+version/credential/health/tags), prompt (+version), dataset
(+version/item/upload), provider (OpenAI/Anthropic/Ollama/Google/Groq/OpenRouter adapters), evaluation
(profiles+versions, jobs, runs, results, events; ~16-metric engine incl. LLM-judge/semantic-similarity/
hallucination/citation), benchmark (+entries/gallery), regression, report, knowledge (**curated KB**:
FAILURE_MODE/REGRESSION/RECOMMENDATION patterns), rootcause (**heuristic** analyzer of jobs/regressions),
advisor (agent/cost/model/prompt/rag), search, debugger, dashboard, analytics.

**Platform V2** — five frozen standalone jars in the same repo, **not referenced by `backend/src`** and
**not in the Spring pom**: kernel (append-only hash-chained bitemporal log; 4 kinds; 5 edge families;
content-addressing; Names/CAS; `resolveAt`; reproduce; subscribe; Postgres store available), knowledge
(ontology: Prompt/Provider/Model/Tool/Agent/Workflow/Dataset/KnowledgeBase/Evaluation/Experiment/
Benchmark/Environment/Policy/Guardrail + Run/Session/Incident/HumanFeedback + EvaluationVerdict/
BenchmarkScore/RootCause/Capability/CostRollup + Deployment/Promotion/Rollback/Approval), fvcs
(Commit/Tag; branch=Name; snapshot=ArtifactPackage), fkge (provenance/impact/dependency/lineage/
root-cause/confidence/evidence/explain/similar/whatChanged/asOf), fxp (`ForgeClient`:
studio/explorer/review/copilot + reproduce/validate/search; `LanguageModel` SPI; adapter SPIs).

**The gap:** two disconnected systems. V1 persists engineering entities in JPA and has its own
(heuristic) knowledge/rootcause/advisor; V2 provides deterministic truth/versioning/reasoning as
libraries with no wiring, controllers, or UI. This plan connects them so V2 becomes V1's engine.

**Overlap map (the decisions hinge on these):**
- V1 `prompt/agent/dataset/provider/evaluation/benchmark` ≈ V2 **Knowledge ObjectTypes**.
- V1 `*_versions` ≈ V2 **FVCS** commits/snapshots.
- V1 evaluation `runs/results` ≈ V2 **Observations (Run)** + **Claims (EvaluationVerdict/BenchmarkScore)**.
- V1 `rootcause` (heuristic) ≠ V2 **FKGE.rootCause** (deterministic causal trace) — different mechanisms.
- V1 `knowledge` (curated KB) ≠ V2 knowledge graph — different concept (relabel to avoid confusion).
- V1 `provider` adapters ≈ V2 `LanguageModel` / `ModelProviderAdapter` SPI implementations.
- V1 `auth/user/org/project/apikey` — **no V2 equivalent** (V2 has no identity); these stay authoritative.

---

## 2. Feature Mapping Matrix

Decision ∈ {Keep, Replace, Wrap, Merge, Remove}. "Wrap" = keep the V1 surface, route its
reads/writes through V2. "Merge" = fold V1 concept into a V2 concept as the new source of truth.

| V1 feature | Decision | Rationale |
|---|---|---|
| Auth (JWT, refresh, verify, reset/OTP) | **Keep** | V2 has no identity; auth is the app's, unchanged. Becomes the source of the kernel `ActorId`. |
| User + roles | **Keep** | Identity/authorization is not a platform concern. |
| Organization + members | **Keep + bridge** | Stays authoritative; `Organization.id → kernel OrgId` (per-org log = hard tenant isolation). |
| Project | **Keep + bridge** | Stays the grouping users navigate; becomes a scoping tag on kernel facts. |
| API keys | **Keep** | Access control at the edge; maps to an actor for attribution. |
| Prompt (+versions) | **Merge → Knowledge `Prompt` + FVCS** | Prompt = V2 Artifact; versions = FVCS commits. JPA table demoted to read-model. |
| Agent (+versions/credentials/health/tags) | **Merge (core) + Keep (ops)** | Agent/versions → Knowledge+FVCS; credentials/health/tags stay JPA (operational, not engineering truth). |
| Dataset (+versions/items/uploads) | **Merge (core) + Keep (blobs)** | Dataset = Artifact (content-hash); versions → FVCS; item/upload storage stays JPA/object store. |
| Provider + model adapters | **Wrap** | Provider = Artifact; adapters become `LanguageModel` + `ModelProviderAdapter` implementations. |
| Evaluation engine (metrics, LLM judge) | **Keep** | No V2 equivalent; valuable. Outputs are wrapped (below). |
| Evaluation profiles (+versions) | **Merge → `Evaluation` + FVCS** | Definition = Artifact; profile versions = FVCS. |
| Evaluation jobs/runs/results | **Wrap → Observations + Claims** | Each run → `Run` observation; verdicts → `EvaluationVerdict`/`BenchmarkScore` claims with evidence (Law 5). |
| Benchmark (+entries/gallery) | **Merge + Keep gallery** | Benchmark = Artifact; scores = `BenchmarkScore` claims; gallery UI stays. |
| Regression checks | **Wrap** | A regression = an `Incident`/`Observation` + causality (`regressed`) edge; FKGE reasons over it. |
| Reports | **Keep, re-source** | Report generation stays; it now reads V2 proofs (provenance/evidence) as content. |
| Knowledge (curated KB) | **Keep, relabel "Playbooks"** | Best-practices library, not a graph; rename to end the name clash with V2 knowledge. |
| RootCause (heuristic) | **Merge → FKGE (+ contributor)** | FKGE.rootCause is authoritative; the heuristic becomes a suggester that emits `RootCause` claims. |
| Advisor (agent/cost/model/prompt/rag) | **Merge → Copilot** | Recommendations become grounded Copilot answers / `Claim`s; the advisor logic feeds Copilot. |
| Search | **Merge** | Keep the UI; back it by `KnowledgeView` + `FKGE.similarTo`/`patterns`. |
| Debugger | **Merge → Explorer/Copilot** | Debugging = provenance/impact/root-cause; fold into Explorer + Copilot. |
| Dashboard / Analytics / Insights | **Keep, augment** | Keep; add V2 signals (impact, confidence, blast radius) as panels. |
| Flyway (identity/ops tables) | **Keep** | Identity/ops schema stays. |
| Flyway (engineering-entity tables) | **Keep → demote** | Retained as **derived read-models** after the system-of-record flip; not removed (query performance). |
| Redis | **Keep** | Rate limit/cache; also caches V2 answers by `(query, asOf)` (safe: content-addressed). |

**Nothing is "Removed" outright** during migration — redundant write paths are retired only in the final
cleanup phase, after V2 is authoritative and verified.

---

## 3. Migration Roadmap (ordered; each phase leaves ONE runnable product, is independently testable,
behind a feature flag, and reversible)

- **P0 — Foundation (invisible).** Add `forge-fxp` (+ transitive fkge/fvcs/knowledge/kernel) and
  `kernel-store-postgres` as backend dependencies (Java 21 already matches). Stand up the kernel against
  the **same Postgres** in a **separate schema** (`PostgresKernels.migrate` at boot). Add the **Org/Actor
  bridge** (Organization.id→OrgId, principal→ActorId). Expose only `validate()`/health internally. Users
  see no change.
- **P1 — Provider bridge (invisible).** Implement `LanguageModel` (Copilot) and `ModelProviderAdapter`
  (record `Run`) over the existing provider adapters. No UI change.
- **P2 — Dual-write + backfill (invisible).** Prompt/Agent/Dataset/Evaluation/Benchmark writes **also**
  append kernel facts (write-through); JPA remains source of truth. Run an **idempotent backfill** that
  replays existing rows and `*_versions` into kernel Artifacts + FVCS commits (validTime = original
  `createdAt`; content-addressing dedups). Verify **parity** (JPA ⇔ kernel projection). Still invisible.
- **P3 — Read-through reasoning (first visible value; additive).** Add Explorer/History/Evidence
  endpoints (FKGE/FVCS) and surface them as **new tabs on existing entity detail pages** (Lineage,
  Evidence, History). No existing workflow changes; only additions.
- **P4 — Review + Copilot (additive).** Add a project-level **Review/Changes** view (commits, semantic
  diff, approvals) and an omnipresent **Copilot** drawer (grounded). Re-back **Advisor** with Copilot.
- **P5 — Flip system-of-record (gated).** Make the kernel authoritative for engineering entities;
  controllers now project reads from kernel-derived read-models. JPA engineering tables become **derived**
  (rebuildable), not primary. Identity/ops tables unchanged. Gate on sustained P2 parity.
- **P6 — Cleanup.** Retire redundant JPA write paths, relabel the curated KB to "Playbooks," fold the
  heuristic root-cause/advisor into their V2-grounded forms, delete dead code.

Every phase ends green and deployable; no phase requires maintaining two products.

---

## 4. Backend Evolution Plan

- **Dependencies:** add `com.broksforge.fxp:forge-fxp:2.0.0` and
  `com.broksforge.kernel:kernel-store-postgres:1.0.0` (both pull only plain-Java V2 jars; no Spring
  conflict; Java 21 matches). Build installs V2 jars to the local/CI repo first.
- **Persistence strategy:** one Postgres instance, **two schemas** — `identity`/app (existing JPA) and
  `forge_kernel` (append log via `PostgresKernels`). The kernel log is the system of record for
  engineering Artifacts/Observations/Claims/Decisions; JPA engineering tables become **derived read-models**.
- **Adapter/bridge layers (all in the app, none in frozen modules):**
  - **Auth/Actor bridge** — authenticated principal → `ActorId`; every write attributed.
  - **Organization bridge** — `Organization.id → OrgId`; every kernel op scoped to the caller's org.
  - **Provider bridge** — existing adapters implement `LanguageModel` + `ModelProviderAdapter`.
  - **Persistence bridge** — a thin `ForgeClient` provider (per-org `Repository.open(kernel, org, actor)`).
- **Service evolution (Wrap, don't rewrite):** Prompt/Agent/Dataset/Evaluation services delegate creates/
  edits to `StudioService` (+ FVCS commit) and project to read-models; the **evaluation metric engine is
  untouched**, only its outputs are recorded as `Run` + verdict claims; `RootCauseEngine` emits
  `Incident`/`RootCause` facts while `FKGE.rootCause` is the authoritative trace; advisors feed Copilot.
- **REST controller evolution:** existing controllers **keep their paths and contracts** (frontend
  unaffected) but delegate to the new application services. **New controllers** are added for Explorer
  (provenance/impact/dependency/lineage/evidence/confidence/explain), History (FVCS), Review
  (reviewCommit/semanticDiff/approve/reject), Copilot (ask) — under the same `/api/v1` base and auth.
- **Frozen-module rule:** no edits to kernel/knowledge/fvcs/fkge/fxp; all mapping is app-side adapters.

---

## 5. Frontend Evolution Plan

- **Pages remain:** every current route stays; navigation is unchanged. No disconnected "V2 section."
- **Pages that evolve (gain V2 capabilities as tabs/panels on the existing detail pages):**
  - Agent/Prompt/Dataset/Evaluation/Benchmark detail → **Versions** tab becomes FVCS **History**; add
    **Lineage** (provenance+dependency), **Impact** (blast radius), **Evidence** (claims+confidence),
    **Explain** panels — each with a "show proof" affordance and the `asOf` stamp.
  - **Studio** = the *existing* create/edit flows, now recording versions/commits — an evolution of what
    users already do, not a new screen. (Optionally a unified authoring workspace later.)
  - **Explorer** = a global page + the per-entity tabs above (reuses org→project→entity navigation).
  - **Review** = a new project-level **Changes/Review** view (commits, semantic diff, approvals) — sits
    beside Evaluations in the project nav.
  - **Copilot** = an omnipresent grounded side-drawer available on every page; **Advisor** becomes a
    Copilot-backed view.
  - **Search** gains structural similarity results; **Dashboard/Analytics** gain impact/confidence panels.
- **API changes:** additive new endpoints only; existing calls unchanged; base URL unchanged. The product
  feels identical, then gains depth — one coherent Broks Forge.

---

## 6. Database Evolution Plan

- **Additive, never destructive.** Introduce the `forge_kernel` schema (managed by `PostgresKernels`)
  alongside the existing schema. Existing Flyway migrations are untouched; kernel schema is created by the
  platform, invoked at boot (or a Flyway callout), so the two evolve independently.
- **What becomes what:**
  - Identity/ops (users, orgs, members, projects, api keys, auth tokens, agent credentials/health, dataset
    uploads) → **stay in JPA** (system of record for identity/ops).
  - Engineering entities (prompts, agents, datasets, providers, evaluations, benchmarks) → **Kernel
    Artifacts**; `*_versions` → **FVCS** commits/snapshots.
  - Evaluation runs/results, regressions → **Observations** (`Run`/`Incident`) + **Claims**
    (`EvaluationVerdict`/`BenchmarkScore`/`RootCause`), with evidence + causality edges.
  - Curated KB → **stays** (reference content).
- **Backfill (no data loss):** an idempotent job replays JPA rows → kernel facts, preserving actor and
  original timestamps (bitemporal `validTime`); content-addressing dedups re-runs. JPA data is **retained**
  as read-models; nothing is deleted until P6 and only for fully-migrated, parity-verified write paths.

---

## 7. Deployment Plan

- **Unchanged pipeline:** GitHub → **EC2** (backend via `docker-compose` + Nginx/certbot) → **Vercel**
  (frontend). `docker-compose.yml` already runs postgres · redis · backend · frontend. No second app, no
  second deployment.
- **Only additive change:** the same Postgres now also hosts the `forge_kernel` schema; `PostgresKernels.
  migrate` runs on backend startup (idempotent). No new service, port, or container. Frontend build/deploy
  is unchanged (new pages are ordinary routes).
- **Config:** reuse `SPRING_DATASOURCE_URL` for the kernel store (same DB); no new required env vars for
  the default path (a Copilot model adapter uses the existing provider credentials).

---

## 8. Testing Strategy

- **Contract tests:** assert every existing REST contract is unchanged across P0–P5 (the frontend must not
  break). Reuse the existing integration tests.
- **Parity tests (P2):** for each dual-written entity, assert the JPA record and the kernel projection are
  equivalent; run continuously as a soak before the P5 flip.
- **Backfill tests:** idempotency (re-run = no duplicates, content-hash dedup) and completeness (row counts
  ⇔ kernel node counts) on a **production snapshot dry-run**.
- **Reasoning tests:** reuse FKGE determinism guarantees; add app-level tests that Explorer/Review/Copilot
  endpoints return proofs carrying `asOf`, and that **Copilot refuses** ungrounded questions.
- **E2E (Playwright, existing `e2e/`):** extend with the three reference workflows surfaced in the UI
  (author→evaluate→decide→explain; incident→root-cause; "why in production" dossier).
- **Migration gate:** P5 flip only proceeds when parity + e2e + contract suites are green on a prod-snapshot
  rehearsal.

---

## 9. Rollback Strategy

- **Per-phase feature flags:** every phase is toggleable; disabling a flag reverts to the prior behavior.
- **P0–P1 (additive):** the kernel schema and bridges are dormant; rollback = turn off.
- **P2 (dual-write):** additive — drop the kernel write to roll back; JPA remains authoritative; the kernel
  is append-only so it never corrupts JPA.
- **P3–P4 (additive UI/endpoints):** hide the new tabs/drawer; existing flows are untouched.
- **P5 (system-of-record flip):** the one gated step — JPA read-models are retained and rebuildable, so the
  flip is reversible by pointing controllers back at JPA until P6 cleanup.
- **Database:** kernel schema is a separate, additive schema — leaving it in place is always safe; existing
  tables are never dropped before P6.
- **Deployment:** redeploy the previous image; because all schema changes are additive, no down-migration is
  required.

---

## 10. Final Unified Architecture

```
                         One Broks Forge (users see one product)
   Next.js frontend (unchanged routes + Studio/Explorer/Review/Copilot tabs & drawer)
                              │  /api/v1 (same base, same auth)
   Spring Boot broks-forge-api
     ├─ Identity & ops:  Auth · User · Org · Project · ApiKey · agent-ops   → JPA (system of record)
     ├─ Experience controllers: existing (evolved) + Explorer/History/Review/Copilot
     ├─ Application services  → ForgeClient (per org/actor)
     │        Studio · Explorer · Review · Copilot        (Platform V2, frozen)
     │        └─ FKGE → FVCS → Knowledge → Kernel
     ├─ Bridges (app-side): Org→OrgId · Principal→ActorId · Providers→LanguageModel/ModelProviderAdapter
     └─ Provider adapters (OpenAI/Anthropic/Ollama/…) — now V2 SPI implementations
   PostgreSQL (one instance): identity/ops schema  +  forge_kernel append-log schema  +  derived read-models
   Redis (cache/rate-limit, + (query,asOf) answer cache)
   Deploy: GitHub → EC2 (compose+nginx) / Vercel — unchanged
```

Engineering truth, versioning, and reasoning are Platform V2 internally; identity, tenancy, ops, and the UI
are the existing app. There is **one** login, org model, project model, backend, frontend, database, and
deployment. Platform V2 is invisible to users — Broks Forge is simply better underneath.

---

## Adversarial Review (7 roles) — attempts to reject the migration

- **AI Platform Engineer — "two knowledge graphs / two root-causes."** Resolved: V1 knowledge is a curated
  KB (relabeled *Playbooks*); V1 root-cause is a heuristic *contributor*, FKGE is authoritative. No dual
  source of engineering truth after P5.
- **Backend Engineer — "risky big-bang rewrite."** Rejected by the dual-write + idempotent backfill +
  append-only kernel + parity-gated flip; services are *wrapped*, not rewritten; the evaluation engine and
  auth are kept.
- **Frontend Engineer — "broken workflows / disconnected V2 section."** Resolved: existing routes and REST
  contracts are invariant through P5; V2 shows up as tabs/panels/drawer inside current pages, not a separate
  area.
- **DevOps — "second deployment / DB migration risk."** Resolved: same pipeline, same containers; kernel
  schema is additive in the same Postgres; startup migration is idempotent; rollback = redeploy prior image.
- **UX Designer — "user disruption."** Resolved: invisible through P2; additive, opt-in depth from P3;
  "show proof"/`asOf` make new power legible without changing familiar flows.
- **Security Engineer — "attribution / tenant isolation / model data egress."** Resolved: every write
  carries the authenticated actor; per-org kernel log = hard isolation; Copilot sends the model only proofs,
  never the graph.
- **Product Engineer — "unnecessary rewrites / tech debt."** Resolved: nothing removed until P6; temporary
  dual-write is the only debt and is explicitly retired; the end-state has one source of truth.

**Residual risks, mitigated:** (a) the P5 flip is the highest-risk step → long P2 soak + parity dashboards +
reversible read-models; (b) backfilling large histories → batch + idempotent + dry-run on a prod snapshot;
(c) kernel read-path fold cost at scale → `(query, asOf)` cache now, indexed-read platform amendment later
(the known FKGE enumeration item) — an amendment, never an app hack. **No major issue remains.**

---

## Success criteria check
✓ one Broks Forge application · ✓ existing users keep the same product · ✓ Platform V2 powers it internally
· ✓ all V2 capabilities available (Studio/Explorer/Review/Copilot/History/Provenance/Impact/Evidence) · ✓
existing deployment intact · ✓ no frozen V2 module modified.

**Plan complete. Nothing implemented.**
