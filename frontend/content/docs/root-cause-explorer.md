# Root Cause Explorer

When an engineer asks *"why?"*, the answer should not be a paragraph. It should be an
**investigation**.

The Root Cause Explorer is the Engineering Investigation Workspace. Open a failed evaluation and it
assembles, in one request, everything the engineering record holds about that failure — arranged as
a chronology, a causal chain and an engineering story.

## What it assembles

One request gathers all of this:

| | |
| --- | --- |
| The evaluation | Its failed runs and their recorded errors |
| The artifacts it ran against | Agent, prompt, dataset, provider, model |
| Their AI Git revisions | Every version, and which is promoted |
| Engineering knowledge | Claims, observations, knowledge objects |
| Decisions | What was chosen about this ground |
| Engineering memory | Why those decisions were made, verbatim |
| Precedents | Earlier failures on the same ground |
| Related evaluations | Other measurements of the same artifacts |
| Blast radius | What downstream depends on this |

No hunting across five pages. That is the whole design goal.

## The engineering timeline

Time is treated as reasoning, not as metadata. Promotions, dataset changes, the run itself, the
moment it broke, the knowledge it produced and the decisions taken afterwards all sit on **one
axis**, oldest first.

```
   │  Support Prompt v2 promoted                          19 days ago
   │  "Softer tone after complaints."
   │
   │  ── 18 days later ──
   │
   ●  Checkout Quality #1 failed on the same ground       yesterday
   │  The record has been here before.
   │
   │  Payments Dataset v3 created                         4 hours ago
   │
   │  Checkout Quality #2 created                         12 minutes ago
   │  Checkout Quality #2 started
   ●  Run #3 failed — Connection refused
   ●  Checkout Quality #2 failed
   │  Decision recorded: ...
```

The gap between two events is labelled when it is long enough to matter. A promotion an hour before
a failure and the failure itself are visible in the same glance — which is the only way an engineer
can see the relationship at all.

## Root cause, at four depths

Stopping at the immediate cause is what makes an error viewer an error viewer. An investigation
separates four layers, and the interface renders them as a descent:

**1 · Immediate cause** — what actually broke. Supplied by the platform's existing failure
classifier, which distinguishes authentication, quota, rate limiting, invalid model, network,
timeout, provider infrastructure, empty output and per-metric failures. Each has a different fix.

**2 · Contributing causes** — what made this failure possible or harder to read. Other classified
failure modes, and the provider every failing run reached.

**3 · Historical causes** — what the record has already lived through. Earlier failures on the same
ground, and decisions that were taken without evidence behind them.

**4 · Related changes** — what moved shortly before this ran. A revision promoted within two weeks
of the run is surfaced with its recorded rationale — and labelled honestly:

> *Proximity is not causation — but a change this close to a failure is the first thing to compare
> against the revision that preceded it.*

Every cause carries its epistemic status, its confidence, the records it rests on, and an action
that tests or resolves it.

## The engineering story

Every investigation answers the same eight questions, each with its own epistemic status and basis:

1. **What happened?**
2. **Why?**
3. **What changed?**
4. **Has this happened before?**
5. **Who or what was affected?**
6. **How confident are we?**
7. **What evidence supports this?**
8. **What should we do next?**

If the record cannot answer one, it says so. A healthy evaluation produces an investigation that
states plainly there is no root cause to find — it does not manufacture one.

## Workflows without losing context

The right rail holds the chains — evidence, precedents, artifacts, AI Git, decisions, knowledge,
related evaluations — plus the Forge Graph. Clicking a timeline event or any referenced record moves
the graph with it. You can follow a thread without leaving the investigation.

Every cause and recommendation continues into a real surface: the failure graph already narrowed to
the break, a revision comparison, the decision page, Engineering Intelligence, or a fresh
investigation of the precedent.

## Brok and the Explorer are one system

Ask Brok to *investigate* a failure and the Explorer opens with the whole investigation already
assembled. Conversely, every investigation ends in follow-up questions that continue in Brok,
carrying the same subject.

They share the same vocabulary — verdicts, epistemic statuses, confidence ladder, action catalogue —
and the same precedent reading, so the two flagship surfaces can never disagree about whether a
failure has happened before.

## How to open one

- The **Investigate** button on any failed evaluation
- The evaluation's **Root cause** tab
- **Investigate this failure** on a red Execution Graph
- Brok's *"Investigate ... with Brok"* recommendation
- Directly:
  `/organizations/<org>/projects/<project>/evaluations/<evaluation>/investigate`

## What it does not do

- **It owns no data.** No table, no second derivation. It is an arrangement of the platform.
- **It does not re-diagnose what the platform already diagnoses.** The immediate cause comes from
  the existing classifier; the Explorer adds the depth around it.
- **It does not guess.** Precedent matching is structural (shared agent, prompt or dataset) plus a
  verbatim comparison of recorded causes. Two failures with reworded errors are reported as *the
  causes differ*, not silently merged.

See also: [Brok](/docs/brok) · [Execution Graph & Failure Graph](/docs/execution-graph) ·
[Evaluations & Metrics](/docs/evaluations)
