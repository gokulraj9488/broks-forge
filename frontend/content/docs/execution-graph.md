# Execution Graph & Failure Graph

The Execution Graph is the runtime path of a single evaluation run, reconstructed from that run's own
recorded telemetry. The Failure Graph is the same graph, entered already narrowed to where the chain
broke.

They are **one model and one surface**, not two features. A failure is an execution whose stages are
in an error state.

## What it shows

Every stage the run actually passed through, with what it produced and what it cost:

```
   ┌──────────────┐
   │    Input     │  the dataset item
   └──────┬───────┘
   ┌──────▼───────┐
   │    Prompt    │  the pinned revision, with variables resolved
   └──────┬───────┘
   ┌──────▼───────┐
   │   Provider   │  the provider actually reached
   └──────┬───────┘
   ┌──────▼───────┐
   │    Model     │  model id, parameters
   └──────┬───────┘
   ┌──────▼───────┐
   │  Inference   │  latency, tokens, cost, HTTP status
   └──────┬───────┘
   ┌──────▼───────┐
   │    Judge     │  metric evaluation
   └──────┬───────┘
   ┌──────▼───────┐
   │    Result    │  pass/fail and score
   └──────────────┘
```

Each node carries its real metadata. Click one for the full detail, including raw error text.

## The break is visible, not described

Data flows along the paths that stayed alive; a failed edge is deliberately **still**. The animation
stopping *is* the visualization of the break — the chain moved until here, then it did not. The
failed node carries a still red glow.

This is why the graph beats a log: it tells you *which component* to fix, not merely that something
failed. A 401 at the provider stage and a low score at the judge stage are both "the evaluation
failed", and they have nothing in common as engineering problems.

## Failures only

The **Failures only** toggle dims every healthy stage so only the broken chain remains.

Arriving from Brok's *"View the failure graph"* — or from an investigation — opens with this already
applied, via `?view=failures`. The engineer lands on the state the answer was about rather than on a
filter they have to find. Handing back work the platform already did would be a small betrayal of
the whole design.

## The evaluation pipeline

Distinct from the graph, and worth knowing about: while an evaluation is *running*, the evaluation
page animates the pipeline as stages complete —

**Dataset → Prompt → Provider → Inference → Judgment → Knowledge → Verdict**

Every stage state is read from real job counters. A hard failure stops the pipeline exactly where
the chain broke, and everything downstream is shown as never reached. Partial failures mark
inference amber with the true count. A failed run keeps its stopped pipeline visible afterwards.

## Exporting

Any execution graph can be exported as PNG, SVG or JSON — for an incident write-up, a review, or a
post-mortem attachment. The JSON carries the nodes, edges, headline and run metadata.

## Where it fits

| Question | Surface |
| --- | --- |
| Where did the chain break? | Execution / Failure Graph |
| Why did it break, and has it before? | [Root Cause Explorer](/docs/root-cause-explorer) |
| What is connected to this evaluation? | [Forge Graph](/docs/forge-graph) |
| What does the record say about it? | [Brok](/docs/brok) |

The Execution Graph answers *where*. It deliberately does not try to answer *why* — that is an
investigation, and it has its own workspace one click away.

See also: [Evaluations & Metrics](/docs/evaluations) ·
[Root Cause Explorer](/docs/root-cause-explorer)
