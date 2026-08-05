# Registry

The Registry is one catalog of everything you engineer: every artifact, and every piece of knowledge
derived from those artifacts.

## An engineering catalog, not a CRUD list

The distinction matters, and it is the reason the Registry exists as its own layer.

A CRUD list shows rows in a table: name, created date, status. One list per module, so discovery is
scattered — prompts here, agents there, datasets somewhere else, and the reasoning about them
nowhere at all.

The Registry shows the **engineering estate**: what exists, what state it is in, what has evidence
behind it, and what has never been measured. Artifacts and the knowledge derived from them sit side
by side, because in engineering practice they are the same question.

## Two scopes

**Artifacts** — agents, prompts, datasets, providers, models, evaluations. Everything you registered
or created.

**Knowledge** — observations, claims, decisions, evidence and knowledge objects. Everything the
platform derived from that work.

Switching between them is one control. That adjacency is the point: *"which prompts do we have"* and
*"which of our prompt decisions have evidence"* are questions the same person asks in the same
minute.

## What it is for

- **Discovery** — one place to find anything, searchable and filterable by type.
- **Triage** — see at a glance what carries a verdict and what is `unknown` (never measured).
- **Navigation** — every entry opens its engineering workspace, on the tab that matters.
- **Orientation** — a new team member can understand the shape of the estate in a few minutes.

## Every entry is a doorway

Opening an artifact from the Registry lands you in its workspace, which carries the four views that
matter:

| Tab | What it holds |
| --- | --- |
| Overview | The artifact itself |
| **Intelligence** | Observations, claims, decisions, evidence, knowledge, memory |
| **Evolution** | Lineage, dependents, impact, and the AI Git timeline |
| Type-specific | Versions, runs, execution graph, root cause — depending on the artifact |

Opening a knowledge object lands you on its own page: what created it, the decision and evidence
behind it, and every artifact it affects.

## Brok is here too

The Registry header carries an **Ask Brok** entry. The natural question from a catalog view is
*"What is the biggest engineering risk right now?"* — which is exactly what it opens with.

## What the Registry deliberately does not do

- **It does not duplicate the module pages.** Agents still have an Agents page; the Registry is the
  cross-cutting view, not a replacement.
- **It does not invent state.** An artifact with no evaluations shows as `unknown`, not as healthy.
- **It does not rank.** There is no score. Ranking by consequence is Brok's job
  (*"What is the biggest engineering risk?"*), and it explains its reasoning when it does it.

See also: [Core Concepts](/docs/core-concepts) · [Forge Graph](/docs/forge-graph) ·
[Evolution](/docs/evolution)
