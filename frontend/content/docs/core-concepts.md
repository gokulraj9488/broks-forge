# Core Concepts

Broks Forge has two kinds of object: things you **create**, and things it **derives**. Almost every
misunderstanding about the platform comes from confusing the two, so start here.

```
   YOU CREATE                         BROKS FORGE DERIVES
   ──────────                         ───────────────────
   Agent          ─┐
   Prompt          │                  Observation   (a measured fact)
   Dataset         ├──► Evaluation ──► Claim         (an assertion, with support)
   Provider        │        │          Decision      (a choice, with a reason)
   Model          ─┘        │          Evidence      (what backs a claim)
                            │          Knowledge     (what you now know)
   Revisions      ──────────┘          Memory        (why it is like this)
   (a promotion is a decision)         Precedent     (it happened before)
```

Nothing in the right column is authored by a human. It is computed on read from the left column.

## Things you create

### Artifact

Anything Broks Forge can reason about: an **agent**, **prompt**, **dataset**, **provider**, **model**
or **evaluation**. Artifacts live in the [Registry](/docs/registry) and are nodes in the
[Forge Graph](/docs/forge-graph).

- **Agent** — an AI system, registered by HTTP endpoint. Framework-agnostic by design: if you can
  call it over REST, Broks Forge can evaluate it.
- **Prompt** — versioned instruction text. The notes you write on a version become Engineering
  Memory.
- **Dataset** — ground truth, versioned. Each row has an input and an expected output.
- **Provider** — a model provider and its credentials, encrypted at rest.

### Revision

One immutable version of a versioned artifact, with its snapshot, its recorded rationale, whether it
is currently **active** (promoted), and whether the platform can roll back to it. Revisions are what
[AI Git](/docs/ai-git) is made of.

### Evaluation

A **reproducible measurement**. Its configuration — agent, prompt, dataset, provider, model — is
pinned when it is created, so reading the result later tells you exactly what was measured. An
evaluation that has produced results is the only thing that can become evidence.

### Run

One dataset item, executed. It records the real output, latency, token counts, cost, HTTP status,
metric results and any error. Runs are what the [Execution Graph](/docs/execution-graph) is
reconstructed from, and what the failure classifier reads.

## Things Broks Forge derives

These five are the reasoning objects. Each has a stable composite id — `decision:prompt-version:<uuid>`
— so it can be linked, cited and navigated to, even though it lives in no table.

### Observation

**A measured fact.** The outcome of an evaluation against an artifact. Observations are the raw
ground truth everything else is built from. They are never opinions.

> *"Checkout Quality measured Refund Agent: 0 of 2 items completed, 2 failed."*

### Claim

**An assertion supported by evidence.** For example, that a particular revision is the canonical one
for an artifact. A claim always cites what supports it, which is what separates it from an assertion
somebody typed into a wiki.

> *"Support Prompt's canonical revision is v3, backed by 4 evaluations."*

### Decision

**An engineering choice, with a reason.** Promoting a revision to active, or deprecating an artifact.
Decisions are the objects most teams lose entirely; here they are derived automatically from the act
itself, and carry the rationale recorded at the time.

> *"Support Prompt v3 was promoted. Recorded reason: 'Softer tone after complaints.'"*

### Evidence

**An evaluation framed as support** for a claim or a decision. The same evaluation is an
*observation* when you ask what was measured, and *evidence* when you ask what backs a decision. The
framing is the information.

### Knowledge

**A durable engineering fact** that emerged from a decision plus evidence. Knowledge only exists
where both are genuinely present — which is why it is trustworthy, and why an artifact nobody has
measured produces none. See [Knowledge](/docs/knowledge).

## The cross-cutting ideas

### Engineering Memory

The answers to *"why is this the way it is?"*, derived from the decisions behind an artifact and
recalled verbatim. New teammates inherit reasoning, not just results.
See [Engineering Memory](/docs/engineering-memory).

### Precedent

An earlier failure that shares an agent, prompt or dataset with the one in front of you. Precedent
is what turns a diagnosis into a lookup: if the same ground failed nineteen days ago, that is the
most useful fact available. Both Brok and the Root Cause Explorer read precedent from the same
place, so they can never disagree.

### Verdict

Every meaningful surface opens with a verdict rather than a table. Exactly five states:

| State | Meaning |
| --- | --- |
| `healthy` | Measured, and nothing is wrong. |
| `attention` | Working, but something needs a human. |
| `risk` | A contradiction or an unproven position that could bite. |
| `failed` | Something is broken now. |
| `unknown` | **Not measured.** Deliberately distinct from healthy — absence is not health. |

### Epistemic status

Every statement declares how it is known. This is enforced by the data model, not by convention.

| Status | Meaning |
| --- | --- |
| `derived` | Read directly from real records. |
| `inferred` | A causal reading of the evidence. It could be wrong, and says so. |
| `suggested` | A recommendation, not a finding. |
| `unknown` | The record cannot answer this. |

### Confidence

A three-step verbal ladder — **consistent with**, **likely**, **near-certain** — never a percentage.
A fabricated number ("87% confident") implies a precision the underlying evidence cannot support.

## How they connect

```
  Prompt ──── has ────► Revision v3 ──── promoted ────► DECISION
                                                          │
                                                     supported by
                                                          ▼
  Dataset ──┐                                          EVIDENCE
            ├──► Evaluation ──► Runs ──► OBSERVATION ──────┤
  Agent  ───┘         │                                    ▼
                      │                                  CLAIM
                      │                                    │
                      │                                    ▼
                      └────────── together produce ──► KNOWLEDGE
                                                          │
                                                          ▼
                                                   ENGINEERING MEMORY
                                                   "why is it like this?"
```

Read [Engineering Intelligence](/docs/engineering-intelligence) for how the derivation actually
works.
