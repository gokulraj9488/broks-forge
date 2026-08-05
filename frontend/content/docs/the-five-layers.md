# The Five Layers

Broks Forge is built as five layers. Each one depends only on the layers beneath it, and each exists
because the layer above could not work without it.

```
  ┌──────────────────────────────────────────────────────────┐
  │  5 · ENGINEERING APPLICATIONS                            │
  │     Brok · Root Cause Explorer · Engineering Briefs      │
  │     Reason over everything below. Own no data.           │
  └──────────────────────────────────────────────────────────┘
                              ▲
  ┌──────────────────────────────────────────────────────────┐
  │  4 · FORGE GRAPH                                         │
  │     The living map: artifacts, real relationships,       │
  │     and reasoning layered on top of them.                │
  └──────────────────────────────────────────────────────────┘
                              ▲
  ┌──────────────────────────────────────────────────────────┐
  │  3 · AI GIT                                              │
  │     Revisions, promotions, rollbacks, rationale.         │
  │     Version control for engineering reasoning.           │
  └──────────────────────────────────────────────────────────┘
                              ▲
  ┌──────────────────────────────────────────────────────────┐
  │  2 · REGISTRY                                            │
  │     One catalog of every artifact and every derived      │
  │     knowledge object. An engineering catalog, not CRUD.  │
  └──────────────────────────────────────────────────────────┘
                              ▲
  ┌──────────────────────────────────────────────────────────┐
  │  1 · FORGE KERNEL                                        │
  │     Identity, tenancy, persistence, execution.           │
  │     The invisible foundation. You never see it.          │
  └──────────────────────────────────────────────────────────┘
```

## Layer 1 — Forge Kernel

**The invisible foundation.** Organizations and projects, users and roles, membership and
authorization, credential encryption, persistence and migrations, evaluation execution, provider
invocation.

You are not supposed to notice this layer. Its job is to make every layer above it able to assume
that data is scoped to the right tenant, that credentials are safe, and that an evaluation actually
ran. When the kernel is doing its job, the product feels like it has no plumbing.

## Layer 2 — Registry

**An engineering catalog, not a CRUD list.** Every artifact — agent, prompt, dataset, provider,
model, evaluation — plus every knowledge object derived from them, discoverable in one place.

The distinction from CRUD matters. A CRUD list shows you rows in a table. The Registry shows you the
engineering estate: what exists, what state it is in, what has evidence behind it, and what has
never been measured. Discovery is one surface, not one per module.

See [Registry](/docs/registry).

## Layer 3 — AI Git

**Version control for engineering reasoning.** Every artifact revision, which one is promoted, what
it superseded, whether it can be rolled back, and the rationale recorded with each change.

This is not source control, and the difference is the point. Source control answers *what text
changed*. AI Git answers engineering questions: what was promoted, why, what it replaced, what
evidence covered it, and whether production is currently running the newest revision or an older one
it was rolled back to.

See [AI Git](/docs/ai-git).

## Layer 4 — Forge Graph

**The living engineering map.** AI systems are graphs, not tables: a prompt is used by an agent,
which is measured by an evaluation, which uses a dataset, which supports a decision. Impact,
lineage, reuse and blast radius are all graph questions, and they are unanswerable in a list view.

The Forge Graph holds the real relationships between real artifacts, and can overlay the derived
reasoning objects onto the artifacts they came from — so you can literally see the thinking sitting
on top of the system.

See [Forge Graph](/docs/forge-graph).

## Layer 5 — Engineering Applications

**Reasoning surfaces that own no data.** Everything at this layer is a *reading* of the layers
below, which is why nothing here can drift from the truth: there is no second copy to drift from.

- **[Brok](/docs/brok)** — the engineering partner. Answers questions from the record, declares how
  every statement is known, and hands you back into the workflow the answer came from.
- **[Root Cause Explorer](/docs/root-cause-explorer)** — the investigation workspace. Assembles a
  chronology, a four-depth causal chain and every evidence chain behind a failure.
- **Engineering Briefs** — eight standing readings of the record (Daily, Deployment, Incident,
  Prompt, Evaluation, Dataset, Knowledge, Architecture), each following the same narrative: what
  happened → why → evidence → impact → recommendation → next action.

Future applications — reports, architecture diff, governance — belong at this layer too, and will be
readings of the same record rather than new subsystems.

## The rule that keeps it coherent

**A layer may read everything beneath it and nothing above it, and no layer may duplicate a layer
below it.**

This is why Brok and the Root Cause Explorer share one precedent reading rather than each
implementing "has this happened before". It is why the Root Cause Explorer reuses the platform's
existing failure classifier for its immediate cause instead of writing a second one. And it is why
adding a reasoning surface never means adding a table.

## The four pillars, and how they map

Every screen in the product belongs to exactly one pillar:

| Pillar | What it covers | Layers involved |
| --- | --- | --- |
| **Build** | Prompts, models, agents, datasets, providers | Registry |
| **Evaluate** | Benchmarks, evaluations, execution, metrics | Kernel + Registry |
| **Understand** | Engineering Intelligence, knowledge, evidence, claims, observations, memory, Forge Graph | Forge Graph + Applications |
| **Evolve** | AI Git, evolution, deployments, rollbacks, promotion | AI Git |

The pillars are the *navigation* model; the layers are the *architecture* model. They are
deliberately different views of the same system.
