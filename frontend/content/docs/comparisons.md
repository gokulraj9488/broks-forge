# Comparisons Overview

## How to read these pages

The tools compared here are good at what they do, and several are more mature than Broks Forge,
have larger teams behind them, and are used in production by more organizations. Nothing on these
pages is intended to suggest otherwise.

The useful comparison is not *which is better* — it is **what each one is for**. These tools mostly
occupy adjacent categories, and the differences are in scope and philosophy rather than in feature
count. Several of them can be run alongside Broks Forge, and for production tracing you probably
should.

Details about other products reflect their generally documented, publicly described capabilities at
the time of writing. Product capabilities change; verify anything decision-critical against the
vendor's own documentation.

## The landscape

```
   PRODUCTION TRACING            EVALUATION / TESTING
   what my system did            is this output good?
   ─────────────────             ────────────────────
   LangFuse                      Promptfoo
   LangSmith                     LangSmith
   Helicone                      LangFuse

   GATEWAY / PROXY               EXPERIMENT TRACKING
   route, cache, control spend   which training run won?
   ───────────────────────       ───────────────────────
   Helicone                      Weights & Biases

   ══════════════════════════════════════════════════
   AI ENGINEERING OPERATING SYSTEM
   why is the system the way it is, and what next?
   ──────────────────────────────────────────────────
   Broks Forge
```

## The distinguishing question

For every tool in that diagram except Broks Forge, ask:

> *"Which of our engineering decisions have no evidence behind them?"*

The question does not parse, because **decision** and **evidence** are not objects those tools
model. That is not a criticism — they were built to answer different questions, and they answer them
well.

Broks Forge exists because that question, and the family it belongs to, are where a lot of an AI
team's time actually goes:

- Why is this prompt the version it is?
- Has this failure happened before, and what did we do?
- What would break if I changed this dataset?
- Can I defend the current configuration in review?
- Why is the system the way it is?

## At a glance

| | Tracing tools | Eval tools | Gateways | Experiment tracking | Broks Forge |
| --- | --- | --- | --- | --- | --- |
| Production request tracing | **Yes** | Partial | **Yes** | No | No |
| Evaluate against datasets | Some | **Yes** | No | Some | **Yes** |
| Versioned artifacts with rationale | Prompts only | No | No | Artifacts | **Yes, all** |
| Decisions as first-class objects | No | No | No | No | **Yes** |
| Evidence linked to decisions | No | No | No | No | **Yes** |
| Engineering memory ("why?") | No | No | No | No | **Yes** |
| Dependency graph of artifacts | No | No | No | Lineage | **Yes** |
| Precedent search over failures | No | No | No | No | **Yes** |
| Assembled root-cause investigation | No | No | No | No | **Yes** |
| Grounded Q&A over the record | Some (LLM) | No | No | No | **Yes (deterministic)** |
| Self-hostable | Often | **Yes** | Often | Partly | **Yes** |
| Maturity / ecosystem | **High** | **High** | **High** | **Very high** | Early |

That last row is honest and important. Broks Forge is a young project. If you need a battle-tested
production tracer with a large support organization behind it, that is not what this is.

## The individual comparisons

- [Broks Forge vs LangFuse](/docs/vs-langfuse)
- [Broks Forge vs LangSmith](/docs/vs-langsmith)
- [Broks Forge vs Promptfoo](/docs/vs-promptfoo)
- [Broks Forge vs Helicone](/docs/vs-helicone)
- [Broks Forge vs Weights & Biases](/docs/vs-weights-and-biases)

And the underlying argument, which applies to all of them:
[Why Observability Is Not Enough](/docs/why-observability-is-not-enough).

## Running them together

This is a genuinely sensible setup, not a diplomatic hedge:

- **A tracing tool** for live production traffic, alerting and per-request debugging.
- **Broks Forge** for the engineering record: versioned artifacts, evidence, decisions, memory,
  investigations and precedent.

They model different objects over different time horizons. A trace matters for hours; a decision
matters for years.
