# Engineering Intelligence

Engineering Intelligence is the reasoning layer of your AI systems: a living, traceable record of
what your engineering produced. What was observed, what was claimed, what was decided, what evidence
supports it, and what durable knowledge emerged.

The important property is that **nobody writes any of it.**

## The problem it solves

Ask a team why their production prompt is the version it is. You will usually get one of three
answers: a shrug, a link to a Slack thread, or a confident story that turns out to be about a
different version.

The reasoning exists — someone had a good reason — but it was never a first-class object. It lived
in a chat window, a PR description, or a head. Wikis are the traditional answer and they fail
reliably, because documenting a decision is a second job that competes with doing the work.

Engineering Intelligence removes the second job. The reasoning objects are **derived from the
engineering act itself**.

## How derivation works

```
   REAL ENGINEERING WORK                 DERIVED, ON READ
   ─────────────────────                 ────────────────

   You run an evaluation      ────────►  OBSERVATION
   against an artifact                   "what was measured"
                                              │
                                              ▼
   You activate a prompt      ────────►  DECISION      ◄── carries the version
   version                               "what was chosen"     notes as its rationale
                                              │
                              ┌───────────────┤
                              ▼               ▼
                          EVIDENCE         CLAIM
                    "what backs it"   "what is asserted"
                              └───────┬───────┘
                                      ▼
                                  KNOWLEDGE
                          "what you now know, durably"
                                      │
                                      ▼
                            ENGINEERING MEMORY
                            "why it is like this"
```

Two acts drive everything: **running an evaluation** and **promoting a version**. Both are things
engineers already do. Neither is extra work.

## The five objects

| Object | Derived from | Answers |
| --- | --- | --- |
| **Observation** | An evaluation's outcome against an artifact | What was measured? |
| **Claim** | A promoted revision plus its supporting evaluations | What do we assert is true? |
| **Decision** | The act of promoting or deprecating | What did we choose, and why? |
| **Evidence** | An evaluation, framed as support | What backs this? |
| **Knowledge** | A decision *and* evidence, together | What do we now know? |

Each carries a stable composite id like `decision:prompt-version:<uuid>`, so it can be linked,
cited, opened as its own page and pointed at from an answer. They are computed on read — there is no
`knowledge` table to fall out of sync.

## Why it is trustworthy

**It cannot be fabricated.** Knowledge only exists where a genuine decision *and* genuine supporting
evidence exist. If you promote a prompt but never evaluate it, no knowledge appears — instead the
platform reports it as an *unsupported decision*, which is itself one of the most useful things it
can tell you.

**It cannot drift.** Because everything is derived on read, there is no copy to go stale. Change the
underlying record and the intelligence changes with it.

**It always shows its work.** Every object links back to the artifacts and evaluations it came from.
Nothing is a summary you have to take on faith.

## Where you see it

- **Any artifact's Intelligence tab** — the full picture for that artifact: its observations, claims,
  decisions, evidence, knowledge and memory.
- **[Registry](/docs/registry)** — the knowledge scope catalogues every derived object across the
  organization.
- **[Forge Graph](/docs/forge-graph)** — toggle *Show reasoning* and the derived objects appear as
  nodes attached to the artifacts they came from.
- **A dedicated page per object** — every observation, claim, decision, evidence and knowledge object
  has its own URL showing what created it and what it affects.
- **[Brok](/docs/brok)** and the **[Root Cause Explorer](/docs/root-cause-explorer)** — both read
  this layer rather than re-deriving it, which is why their answers agree with what the Intelligence
  tab shows.

## The questions it makes answerable

These are ordinary engineering questions that are surprisingly hard without this layer:

- *Which of our decisions have no evidence behind them?* — an unsupported promotion is a position
  the organization is carrying on faith.
- *Do any of our claims contradict the evidence?* — a claim that a revision is canonical, sitting
  beside failing evaluations of that same artifact, is a contradiction worth surfacing.
- *What do we actually know about this prompt?* — as opposed to what we assume.
- *What has never been measured?* — reported as `unknown`, never as healthy.

## Design rules

These are enforced in the codebase, not aspirational:

1. **Derive, never store.** No reasoning object gets its own table.
2. **Never fabricate.** An object exists only where its real inputs exist.
3. **Absence is not health.** Unmeasured is `unknown`, a distinct verdict state.
4. **Everything is traceable.** Every object links to the records it was derived from.
5. **One derivation, many readers.** Brok, the Root Cause Explorer, briefs and the graph all read
   the same derivation. None of them re-implements it.
