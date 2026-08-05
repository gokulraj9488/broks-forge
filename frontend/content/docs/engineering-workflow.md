# The Engineering Workflow

Broks Forge is organised around a loop, not a set of pages. The loop is:

**Problem → Execution → Evidence → Knowledge → Decision → Revision → Promotion → Deployment →
Learning**

Every surface in the product exists to serve one step of it, and the loop closes: learning feeds the
next problem.

```
      ┌──────────────────────────────────────────────────────┐
      │                                                      │
      ▼                                                      │
   PROBLEM ──► EXECUTION ──► EVIDENCE ──► KNOWLEDGE ──► DECISION
   "quality    run a real   the result   what you      promote,
    dropped"   evaluation   on record    now know      or don't
                                                          │
                                                          ▼
   LEARNING ◄── DEPLOYMENT ◄── PROMOTION ◄──────────── REVISION
   memory,      production     the active            a new version,
   precedent    is running     revision              with a reason
                this
```

## 1 · Problem

Something is wrong, or something could be better. Quality dropped, cost rose, a customer complained,
an evaluation failed overnight.

**Where you start:** the dashboard's Engineering Brief, or ask Brok *"How is my system doing?"* or
*"What should my team work on next?"* — which returns the attention queue ordered by consequence
rather than by date.

If the problem is a failure, go straight to the
[Root Cause Explorer](/docs/root-cause-explorer) instead of reading logs.

## 2 · Execution

You measure. An [evaluation](/docs/evaluations) runs your agent against a dataset with a pinned
configuration, producing real runs with real outputs, latency, cost and metric results.

**This is the step that generates truth.** Everything downstream is derived from it, and an
artifact that skips this step is permanently `unknown` — never healthy.

## 3 · Evidence

The result lands in the record. It becomes an [Observation](/docs/core-concepts), and where it
covers a promoted revision it becomes **Evidence**.

**Where you see it:** the artifact's Intelligence tab, the Registry's Knowledge scope, or by asking
Brok *"Show me the evidence."*

## 4 · Knowledge

Where a decision and evidence both exist, [Knowledge](/docs/knowledge) emerges — a durable
engineering fact, linked to what produced it.

Where they do not, the platform tells you that instead: an *unsupported decision*, or a
*contradiction* between a claim and the evidence around it. Both are more useful than silence.

## 5 · Decision

You choose. Promote or don't; roll back or hold.

**Where the platform helps:** ask Brok *"Should I promote it?"*. It weighs the evidence that covers
the candidate revision specifically, and refuses to bless an unmeasured one:

> *"Nothing has measured v4, so promoting it would be an act of faith. A promotion with no evidence
> behind it cannot be defended later and cannot be safely reversed either."*

That refusal is the feature. A partner that agrees with everything is not a partner.

## 6 · Revision

You create a new version of the artifact — and **you write the reason**.

This is the single highest-leverage habit in the platform. That sentence becomes the rationale on
the derived Decision, and from there it becomes
[Engineering Memory](/docs/engineering-memory), recalled verbatim forever.

One honest sentence. *"Softer tone after complaints."* It costs five seconds and it is the
difference between a system that can explain itself in a year and one that cannot.

## 7 · Promotion

You activate the revision. [AI Git](/docs/ai-git) records it: what was promoted, what it superseded,
when, and whether the previous revision remains rollback-ready.

Promotion is a separate act from creation, which is exactly why it is a decision worth recording.

## 8 · Deployment

Production runs the promoted revision. The deployment timeline shows the truth, including the
uncomfortable one: if the active revision is not the newest, that is a **rollback**, and it is
displayed as one rather than quietly implied.

## 9 · Learning

The loop closes. What you learned is now part of the record — and, critically, part of the
**precedent** available to the next failure.

Six weeks later, when something breaks, *"Has this happened before?"* returns this incident, its
cause, what you decided, and why. That is the payoff for everything above.

## Where each surface fits

| Step | Primary surface |
| --- | --- |
| Problem | Engineering Brief · Brok · Root Cause Explorer |
| Execution | Evaluations |
| Evidence | Intelligence tab · Registry (Knowledge) |
| Knowledge | Knowledge objects · Forge Graph reasoning overlay |
| Decision | Brok (`promotion.advice`) · Decision pages |
| Revision | Prompt/agent/dataset versions |
| Promotion | AI Git |
| Deployment | Deployment timeline |
| Learning | Engineering Memory · precedent |

## The shortest useful loop

If you do nothing else:

1. Register an agent, a dataset and a prompt.
2. **Write a reason on every prompt version.**
3. Run an evaluation before every promotion.
4. When something fails, open the investigation instead of the logs.

Those four habits produce a record that answers almost every question in this documentation.

See also: [Examples](/docs/examples) · [Best Practices](/docs/best-practices)
