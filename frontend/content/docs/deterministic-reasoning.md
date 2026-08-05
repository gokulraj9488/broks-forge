# Deterministic Engineering Reasoning

Broks Forge has a reasoning layer — [Brok](/docs/brok) and the
[Root Cause Explorer](/docs/root-cause-explorer) — and it contains **no language model**.

That surprises people, so this page explains the reasoning behind it. It is the most consequential
architectural decision in the platform.

## The problem with a generative answer

Suppose you ask an LLM-backed assistant, wired to your engineering data: *"Has this failure happened
before?"*

It will answer. It will answer fluently, in your terminology, with the right shape. And it will
answer just as fluently whether or not a precedent exists — because generating a plausible answer is
what the model does. The failure mode is not that it is wrong; it is that **a wrong answer is
indistinguishable from a right one**.

For an engineering partner, that is disqualifying. The whole value of asking "has this happened
before?" is that a *no* is as actionable as a *yes*. An answer you have to verify by hand has saved
you nothing.

## What deterministic means here

Brok resolves your question to an **engineering intent** by explicit weighted phrase scoring, then
composes the answer from database rows through a single, narrow data channel.

```
   Your question
        │
        ▼
   Intent resolution        deterministic phrase scoring,
   (25 real intents)        explicit weights, a threshold
        │
        ▼
   One read of the          agents, prompts, datasets, providers,
   engineering record       evaluations, runs, derived knowledge
        │
        ▼
   Answer composition       every sentence built from fields
                            that exist in that snapshot
```

The record snapshot is the *only* channel through which content can enter an answer. There is no
second path — no free-text generation step, no template filled by a model.

**This makes fabrication structurally impossible rather than discouraged.** It is not a prompt
instruction that says "do not hallucinate"; it is an architecture in which there is nothing to
hallucinate *with*.

## What you gain

**Refusal is reliable.** Ask something outside the record and you get an explicit *"the engineering
record cannot answer that"* plus the questions it can answer. The refusal is a fact about the
record, not a mood.

**Answers are reproducible.** The same question against the same record gives the same answer. You
can cite an answer in a review.

**Every claim is checkable.** Each statement carries the records it was read from. Nothing is a
summary you have to trust.

**No data leaves.** Answering a question requires no external API call, so your engineering record
is not sent to a model provider to be reasoned about.

**It is free and instant.** No tokens, no rate limits, no latency budget for reasoning.

## What you give up, honestly

This is a real trade, and pretending otherwise would be exactly the kind of claim this platform
exists to avoid.

- **Brok understands engineering intents, not arbitrary language.** A question phrased far outside
  the 25 supported intents resolves to *unknown* and is refused, even if the record could in
  principle answer it.
- **No open-ended synthesis.** It will not write you an essay comparing your architecture to
  industry practice. It answers engineering questions about your record.
- **Coverage grows by engineering.** Supporting a new question means adding an intent and a handler,
  not writing a better prompt.

We consider that trade clearly worth it for a partner whose answers are meant to be *relied on*. A
tool that is right 90% of the time in an unpredictable 10% is worse than a tool with a smaller,
known boundary.

## Where inference is allowed — and labelled

Determinism does not mean the platform only states the obvious. It draws causal readings too. The
difference is that they are **marked**.

| Status | Meaning | Example |
| --- | --- | --- |
| `derived` | Read directly from records | *"6 of 8 items completed, 2 failed."* |
| `inferred` | A causal reading that could be wrong | *"The failures read as infrastructure rather than quality."* |
| `suggested` | A recommendation | *"Check the provider before changing the prompt."* |
| `unknown` | The record cannot answer | *"Nothing has measured this revision."* |

Paired with a three-step confidence ladder — *consistent with*, *likely*, *near-certain* — instead of
a fabricated percentage.

So when the Root Cause Explorer says a prompt promoted two hours before a failure is a *related
change*, it labels that `inferred` and states plainly: **proximity is not causation**. The platform
is allowed to reason; it is not allowed to pretend a reading is a fact.

## Could a model be added later?

It could, in one specific place: mapping unusual phrasings onto the existing intents. That would
widen the front door without touching the answer path, so answers would stay composed from real
rows.

What will not change is the guarantee. Content in an answer comes from the engineering record, or it
does not appear.

See also: [Brok](/docs/brok) · [Root Cause Explorer](/docs/root-cause-explorer) ·
[Engineering Intelligence](/docs/engineering-intelligence)
