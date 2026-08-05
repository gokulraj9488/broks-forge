# Data Model

Broks Forge stores a small number of real tables and derives everything else on read. Understanding
that split is understanding the platform.

## Stored entities

All persisted, Flyway-migrated, and scoped by organization (and usually project).

### Identity and tenancy

| Entity | Notes |
| --- | --- |
| `users` | Accounts, credentials, verification state |
| `organizations` | The top-level tenant |
| `organization_members` | Membership with a role: `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` |
| `projects` | A workspace inside an organization |
| `api_keys` | Programmatic access, hashed at rest |

### Artifacts

| Entity | Versioned | Notes |
| --- | --- | --- |
| `agents` | ✅ `agent_versions` | Registered by HTTP endpoint; framework and language are metadata |
| `prompts` | ✅ `prompt_versions` | Template text; the notes field becomes Engineering Memory |
| `datasets` | ✅ `dataset_versions`, `dataset_items` | Ground truth: input + expected output per item |
| `providers` | — | Model provider config; credentials encrypted |

A versioned artifact carries a pointer to its **current active version** — the promoted revision.
That pointer moving is the act that derives a Decision.

### Evaluation

| Entity | Notes |
| --- | --- |
| `evaluation_jobs` | The pinned configuration and lifecycle: `PENDING → RUNNING → COMPLETED / FAILED / CANCELLED` |
| `evaluation_runs` | One per dataset item: output, latency, tokens, cost, HTTP status, error. `PENDING / RUNNING / SUCCEEDED / FAILED` |
| `evaluation_results` | Per-metric outcome for a run: passed, score, detail |
| `evaluation_profiles` | Reusable metric configurations |
| `benchmarks`, `benchmark_entries` | Variant comparison |
| `regression_checks` | Candidate vs baseline |
| `reports` | Generated report records |

## Derived objects

**None of these have tables.** They are computed from the rows above every time they are read.

| Object | Derived from |
| --- | --- |
| **Observation** | An evaluation's outcome against an artifact |
| **Claim** | A promoted revision plus the evaluations covering it |
| **Decision** | The act of promoting or deprecating, carrying the version's notes as rationale |
| **Evidence** | An evaluation, framed as support for a claim or decision |
| **Knowledge** | A Decision *and* Evidence, together |
| **Engineering Memory** | The rationale on Decisions, recalled verbatim |
| **Forge Graph** | Real references between artifacts |
| **Evolution** | Graph traversal — dependencies, dependents, transitive impact |
| **AI Git timeline** | Version rows plus active/rollback state |
| **Precedent** | Earlier troubled evaluations sharing an artifact |
| **Investigations, Brok answers, briefs** | Readings of all of the above |

### Composite ids

Derived objects need stable, linkable identity without a primary key. They use composite ids:

```
   decision:prompt-version:3f2a…      a decision about a prompt version
   evidence:evaluation:9c1b…          an evaluation framed as evidence
   knowledge:prompt:7e44…             knowledge about a prompt
   observation:evaluation:0ab2…       a measured outcome
   claim:agent:5d90…                  a claim about an agent
```

Artifact node ids follow the same shape — `prompt:<uuid>`, `evaluation:<uuid>`, `run:<uuid>` — and
are used consistently by the graph, the Registry, Brok references, investigation timelines and deep
links. **One id vocabulary across the entire product.**

This is also how grounding is verified: an automated check asserts that every reference in every
Brok answer and every investigation starts with a known prefix, which makes a fabricated reference a
test failure.

## Why derive instead of store

| | Stored reasoning | Derived reasoning |
| --- | --- | --- |
| Drift | Inevitable | Impossible |
| Migration when logic improves | Backfill required | None |
| Can be fabricated | Yes — anything can be inserted | No — no insert path exists |
| Cost | Cheap reads | Some CPU per read |
| Deletion / correction | Must cascade by hand | Automatic |

The cost is real. Assembling an investigation reads the evaluation, its runs, the artifacts, their
revisions, the knowledge catalog and the precedent set. At the scale this platform targets — an
engineering estate, not a traffic firehose — that is the right trade, and it buys a guarantee that
would otherwise be a promise.

## Entity relationships

```
   organization
     ├── organization_members ── users
     └── project
          ├── agent ──────── agent_version
          │     └── provider
          ├── prompt ─────── prompt_version
          ├── dataset ────── dataset_version ── dataset_item
          └── evaluation_job
               ├── (pins agent, prompt_version, dataset_version, provider, model)
               └── evaluation_run ── evaluation_result
```

An evaluation job **pins** its inputs rather than referencing them loosely — which is what makes a
result reproducible and what lets an investigation say exactly what was measured.

## Time

Two timestamps matter for reasoning:

- **`createdAt`** — when the record was made.
- **`completedAt`** — when an evaluation finished.

The platform reads "when did this happen" as *completion where recorded, otherwise creation*. That
single rule makes the [engineering timeline](/docs/root-cause-explorer) orderable across objects
that finish at different points in their lifecycle.

See also: [Architecture Overview](/docs/architecture) · [Core Concepts](/docs/core-concepts) ·
[REST API](/docs/rest-api)
