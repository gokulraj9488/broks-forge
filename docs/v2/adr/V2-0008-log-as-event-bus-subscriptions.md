# V2-0008. The log as the event bus; subscription as the sole autonomy model

- Status: Accepted
- Date: 2026-07-28
- Level: Conceptual (no implementation content)

## Context

The V2 vision demands an event-driven operating system and an autonomous engineering layer:
nightly passes, failure→regression-test generation, suggestion engines, engineering memory
that surfaces unprompted. Conventionally these arrive as separate machinery — a message
broker beside the database, cron jobs beside the broker, and "AI features" as privileged
pipelines whose workings are invisible.

Every added mechanism is a second place where truth can diverge and a place where autonomy
can escape the laws (an "AI insight" produced by an unauditable pipeline violates the Claim
law in spirit even if its output is later wrapped). The design question: what is the
*minimum* event model that powers full autonomy without creating a second system of record
or a privileged execution path?

## Alternatives considered

- **A message broker beside the graph.** Two histories (the log of events and the graph of
  facts) that must be reconciled forever; events can fire without facts and facts can land
  without events. The two-truths problem, reintroduced.
- **Scheduled features (cron-style nightly jobs as bespoke modules).** Each autonomous
  behavior becomes its own machinery with its own audit story; the platform's intelligence
  fragments into unrelated robots.
- **Imperative automation hooks (user-defined webhooks/scripts on mutation).** Powerful and
  lawless: outputs land outside the graph, provenance evaporates, and the hook layer
  becomes the unauditable part of the system.
- **A privileged internal AI pipeline** ("the intelligence layer"). Structurally exempt
  from the laws it enforces on others — the fastest possible way to forfeit the trust
  argument.

## Decision

**The append log is the event stream.** Every append is an event; every event is an append;
the engineering record and the event history are one structure and cannot disagree. Log
position is the causal clock; wall-clock time is an attribute.

**All reaction is `subscribe`:** a standing traversal pattern bound to a program. When new
appends match, the program runs; everything it produces lands as appends, signed by the
program as an actor, under all ten laws (Law 9: no privileged writer). Scheduled behavior
is a subscription to the passage of log time — a degenerate pattern, not a second mechanism.

The entire V2 autonomy catalogue is subscriptions, not modules: failure→regression-test
(subscribe on incident observations, append a derived test artifact), regression detection
(subscribe on evaluation observations, diff against prior closure, append a claim),
suggestions (subscribe on regression claims, append claims-with-actions citing historical
decisions), engineering memory (subscribe on incidents, traverse for similar subgraphs,
append seen-before claims), the nightly report (a lens over the night's appended claims).

**The learning loop is closed by the same mechanism:** when a claim's action is adopted by
a decision and later observations confirm or refute the expected improvement, the producing
method's calibration is adjusted — Forge's judges are scored by the graph they write to.

## Consequences

**Positive**
- One mechanism, entire autonomy story; adding an autonomous behavior is writing a
  userspace program, never extending the kernel.
- Autonomy is inspectable by construction: what fired, why, on what evidence, producing
  which appends — the same traversal as for any actor's work.
- Deterministic reprocessing: subscriptions over an append-only log can be replayed from
  any position, so autonomous behavior is testable against history.

**Negative / trade-offs**
- Subscription storms are possible (appends triggering programs whose appends trigger
  programs); the model needs cycle-visibility and budget semantics specified in the domain
  model — declared openly rather than discovered in production.
- Everything-through-the-log makes log throughput the system's heartbeat; this is an
  implementation burden accepted knowingly, because the alternative (side channels) costs
  the truth guarantee.
