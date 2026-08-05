# Engineering Principles

The rules this codebase is actually held to. They are enforced in review and, where possible, in
tests.

## 1 · One product

There is **one** Broks Forge. One navigation, one engineering language, one identity.

No parallel apps, no second dashboard, no duplicate pages, no alternative API for the same data.
When a new capability arrives, the first question is not "where does this page go?" but "which
existing surface does this evolve?"

## 2 · Evolve, don't duplicate

If a capability already exists, extend it. Adding a second implementation is the most expensive
mistake available, because the two immediately begin to disagree.

Worked examples from this codebase:

- The Root Cause Explorer reuses the platform's existing failure classifier for its immediate cause
  rather than writing a second one.
- Brok and the Explorer share **one** precedent reading, so they can never disagree about whether a
  failure has happened before.
- The Failure Graph is the Execution Graph entered in a narrowed state — one model, one route, one
  truth — not a second surface.
- The Explorer reuses Brok's DTO vocabulary, so the same components render both.

## 3 · Derive, don't store

Reasoning objects are computed on read. No table for Observation, Claim, Decision, Evidence,
Knowledge, Memory, the graph, or an investigation.

Derived state cannot drift, needs no migration when the logic improves, and — most importantly —
cannot be fabricated, because there is no insert path.

## 4 · Never fabricate

The hardest rule and the most important.

Content enters an answer through the engineering record or it does not appear. The reasoning layer
has **no language model**, so this is structural rather than aspirational — there is nothing to
fabricate with.

When the record cannot answer, the system says so and offers what it can answer.
See [Deterministic Engineering Reasoning](/docs/deterministic-reasoning).

## 5 · Absence is not health

An artifact nobody has measured is `unknown`, never `healthy`. `unknown` is a distinct verdict state
for exactly this reason, and it is deliberately not styled as good news.

Reporting "no failures" for something that was never evaluated is a lie the interface refuses to
tell.

## 6 · Every statement declares how it is known

`derived` · `inferred` · `suggested` · `unknown`. One per statement, no exceptions.

Confidence is a three-step verbal ladder — *consistent with*, *likely*, *near-certain*. Never a
percentage, because a number implies a precision the evidence cannot support.

## 7 · Every answer continues into work

An answer that ends in prose is a dead end. Every recommendation carries an action into a surface
that **already exists**.

The API returns action *kinds*; the client resolves routes. An action that cannot be resolved
renders without a link rather than pointing somewhere that does not exist.

## 8 · Colour carries one meaning

Structural hues say **what a thing is**. The verdict palette says **how it is going**. The two are
never mixed, so a colour is never ambiguous.

## 9 · Quality is never reported without its price

Wherever quality appears, latency and cost appear with it. A prompt that is three points better and
four times more expensive is not straightforwardly better, and the interface will not imply that it
is.

## 10 · Tenancy is checked twice

Membership at the controller, re-scoping in the owning service. A resource in another tenant is a
**404**, never a 403.

An automated deny-by-default test asserts that an endpoint without an explicit authorization rule is
inaccessible.

## 11 · Test against reality

Integration tests run against real PostgreSQL via Testcontainers, not an in-memory substitute.
Migrations, constraints and SQL behaviour are exercised as they will run in production.

Tests assert **properties**, not phrasing: that every reference resolves to a real record, that
every action names a known surface, that an unanswerable question cites zero evidence.

## 12 · Comments explain why

Code says what it does. A comment exists to say why it does it that way — the constraint, the
trade-off, the failure it prevents.

> *"The Failure Graph is the Execution Graph in its red state — one model, one route, one truth.
> Only the label changes, because 'view the failure graph' is what the engineer is actually doing."*

Not: *"// opens the failure graph"*.

## 13 · Honest reporting

A test that fails is reported as failing. A step that was skipped is reported as skipped. Work is
"done" when it is verified, not when it compiles.

This applies to the product too: the platform reports what it cannot do as readily as what it can.

## The identity test

Every design decision is checked against one question:

> **Does this reinforce that Broks Forge is an AI Engineering Operating System?**

A change that makes it look more like an evaluation tool, an observability dashboard, or a chatbot
with a database fails that test regardless of how useful the feature is in isolation.

See also: [Project Rules](/docs/project-rules) · [Coding Standards](/docs/coding-standards) ·
[Contributing](/docs/contributing)
