# V2-0004. The Decision as a kernel kind

- Status: Accepted
- Date: 2026-07-28
- Level: Conceptual (no implementation content)

## Context

The V2 vision proposed that every engineering action become a first-class, replayable
Decision — "Why did Prompt v32 exist? Why was GPT replaced? Why was the rollback executed?"
— so that engineering history is replayable as *reasoning*, not just as execution. The
proposal had to survive the primitive test: can Decision be expressed by an existing kind
instead of becoming a fourth one?

## Alternatives considered

- **Decisions as commit messages / description fields on revisions.** The Git model.
  Unstructured, uncited, unqueryable — and structurally incapable of recording a decision
  that produced no revision.
- **Decisions as Claims.** Category error. A claim is truth-apt: it can be right or wrong
  and can be superseded by better reasoning over the same evidence. A decision is an act of
  will: neither true nor false, only made — and it cannot be un-made by argument, only
  followed by another decision. Different laws of revision mean different kinds
  (ADR-V2-0002).
- **Decisions as the revisions they produce** ("the new version *is* the decision").
  Fails on the empty case: approve, reject, defer, roll back, accept-the-risk produce **no
  artifact change**, and the recorded choice *not* to change is often the most consequential
  engineering fact of a quarter. A history that only records changes cannot record judgment.
- **Decisions in an external system (tickets, docs, ADR markdown).** V1's own practice.
  Severed from the graph, they cannot be cited by claims, traversed from incidents, or
  replayed in context; they rot independently of the record.

## Decision

**Decision is the fourth kernel kind.** A decision node records: the actor who made it, the
alternatives considered, the claims it cites as its basis (or an explicit self-declaration
as a judgment call — Law 6), what it produced (possibly nothing), and the log position at
which it was made.

Consequences of the definition rather than additions to it:

- **Decision replay**: `reproduce(decision)` reconstructs the exact context — what was
  known, believed, and weighed at that log position — and can re-decide under a modified
  closure (the what-if).
- **The AI Pull Request** is three appends: a proposed decision, review claims attached to
  it, an accepting decision.
- **Architecture Decision Records become native**: an ADR is a decision node, finally
  queryable and citable rather than a markdown convention.
- **Deployment is a decision** (a name repointing, ADR-V2-0006) followed by observations of
  the rollout — will and reality, correctly separated.

The judgment-call escape hatch is deliberate: forcing every human choice to manufacture
evidence breeds fake evidence. An honest "gut call, signed" is a real engineering fact;
a laundered one is poison. Honesty outranks ceremony.

## Consequences

**Positive**
- Organizational memory extends to judgment: "why did we choose this?" has a permanent,
  navigable answer, surviving the departure of everyone who was in the room.
- Accountability without archaeology: every consequential change traces to a signed choice
  with its cited basis.
- The engineering loop closes: Claim → Decision → new Artifact revision is the arc that
  turns knowledge back into design, and it is now recorded.

**Negative / trade-offs**
- Recording decisions has a human cost; if tooling makes it feel like paperwork, engineers
  will decide outside the system and backfill fiction. The mitigation is constitutional
  (the escape hatch) and practical (decisions must be capturable in one gesture at the
  point of action).
- Proposed-but-never-resolved decisions will accumulate; staleness must be visible
  (status is a query — Law 10), not silently embarrassing.
