# Why Observability Is Not Enough

This page is not an argument against observability. Observability tools are excellent at what they
do, and if you run AI in production you should have one. The argument is narrower and more useful:
**there is a class of engineering question they structurally cannot answer**, and that class is
where most of an AI team's time actually goes.

## What observability models

An observability platform models **execution**. Its atoms are:

- a request, a response, a span
- latency, tokens, cost
- a status code, an error, a stack trace
- a timestamp

This is the right model for "what did my system do?", and it scales to enormous volume.

## What engineering questions are about

Engineering questions are not about requests. They are about **artifacts, versions, decisions and
evidence** — objects with different lifetimes and different relationships.

Consider a real morning. An evaluation failed overnight. You ask, in this order:

1. What broke? → *a trace answers this*
2. Which stage of the chain stopped? → *a trace answers this*
3. Has this failed before? → **needs a searchable history of failures**
4. What did we do about it last time? → **needs decisions linked to failures**
5. Did anything change just before this ran? → **needs versioned artifacts with timestamps**
6. Why was that change made? → **needs the rationale recorded with the change**
7. What else depends on the thing that changed? → **needs a dependency graph**
8. Can I defend the current configuration in review? → **needs evidence linked to decisions**

Questions 1–2 are observability. Questions 3–8 are engineering, and no volume of traces produces
them, because the objects they refer to were never modelled.

## The structural gap

```
   OBSERVABILITY                   AI ENGINEERING OS
   ─────────────                   ─────────────────
   request                         artifact
   span                            revision
   trace                           evaluation
   metric                          observation
   log line                        claim
   error                           decision
   timestamp                       evidence
                                   knowledge
                                   memory
                                   precedent
```

The right column is not a richer version of the left. It is a different model, over a different
time horizon. A trace is interesting for hours; a decision is interesting for years.

You cannot derive "this promotion has no evidence behind it" from traces at any sampling rate,
because *promotion* and *evidence* are not concepts a tracer has.

## Three concrete examples

**"Why is the temperature 0.2?"**
Observability can show you every request that used 0.2. It cannot tell you that someone set it
deliberately after a hallucination incident. That is [Engineering Memory](/docs/engineering-memory).

**"Which of our decisions are unproven?"**
Observability has no concept of a decision, so this question does not parse. In Broks Forge it is a
first-class query, and the answer is usually uncomfortable.

**"Has this happened before?"**
A log search can find similar error strings if you guess the right substring and the retention
window is long enough. It cannot tell you that the earlier failure shared a dataset with this one,
what the team decided afterwards, or whether that fix held. That requires failures, artifacts and
decisions to be connected objects.

## Why "just add dashboards" does not close the gap

The usual response is to build dashboards on top of traces. It fails for three reasons:

1. **The objects are missing.** You cannot chart decisions you never recorded.
2. **Retention is wrong.** Traces are sampled and expire; engineering decisions must not.
3. **The relationships are missing.** "What depends on this dataset?" is a graph traversal, not an
   aggregation.

## They are complementary

The honest position, and the one Broks Forge takes:

| Use observability for | Use an AI Engineering OS for |
| --- | --- |
| Live production traffic | The engineering record behind the system |
| Alerting on latency, error rate, spend | Deciding whether a change is defensible |
| Debugging a specific user's request | Investigating why an evaluation keeps failing |
| High-volume sampling | Durable, unsampled decisions and evidence |
| What happened last night | Why the system is the way it is |

Broks Forge does not try to be your production tracer. It does not sample your traffic, and its
Execution Graph is reconstructed from an evaluation's own recorded runs rather than from live
requests.

What it does instead is hold the part of your engineering that observability was never designed to
hold — and then reason over it.

See also: [The AI Engineering Operating System](/docs/ai-engineering-operating-system) ·
[Comparisons Overview](/docs/comparisons)
