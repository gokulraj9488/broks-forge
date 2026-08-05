# V2-0002. Epistemic Typing — the four kernel kinds

- Status: Accepted
- Date: 2026-07-28
- Level: Conceptual (no implementation content)

## Context

A graph in which "everything is a node" is vacuously true and therefore useless — the same
way "everything is an object" constrains nothing. The substrate (ADR-V2-0001) needs an
ontology: a fixed set of node kinds with real semantics. The obvious candidate — a domain
taxonomy (Agent, Prompt, Incident, Evaluation, …) — is an open, ever-growing list with no
principle deciding what belongs, which is how tools accrete features instead of concepts.

The design question: what is the *smallest closed* set of kinds such that every engineering
fact is expressible, and what principle closes the set?

## Alternatives considered

- **Untyped nodes with free-form labels.** Maximum flexibility, zero semantics: no law can
  attach to a label, so honesty (evidence requirements, immutability of the past) becomes
  convention again. Rejected.
- **Domain taxonomy as the kind system** (Agent, Prompt, Dataset, Incident, … as kernel
  types). Open-ended, implementation-driven, and wrong in kind: an Incident and an
  Evaluation differ in subject matter but behave identically as records. Subject matter is
  metadata; behavior under revision is what needs typing. Rejected as *kernel* typing; kept
  as an open subtype registry (ADR-V2-0010).
- **Three kinds (no Decision).** Fails: choices that change nothing (approve, reject, defer,
  accept-the-risk) are unrepresentable, and they are among the most important facts an
  engineering organization produces. See ADR-V2-0004.
- **Five kinds (Actor as a kind).** Fails the reduction test: every append already carries an
  actor identity as a substrate law; the actor node is reified provenance, not a fifth
  epistemic status.
- **Two kinds (things and events).** Fails: it cannot distinguish a measured fact from a
  derived belief, so a model-graded score and a raw latency reading have the same standing —
  the exact confusion that makes existing tools untrustworthy.

## Decision

Every node is exactly one of **four kinds**, typed by **epistemic status** — the fact's
relationship to truth — because epistemic status is what determines the correct *law of
revision*:

| Kind | Status | Law of revision |
|---|---|---|
| **Artifact** | Intent — designed | Superseded by new revisions |
| **Observation** | Reality — happened | Never revised; only annotated or re-measured |
| **Claim** | Belief — interpreted | Superseded by better reasoning or new evidence |
| **Decision** | Will — chosen | Never unmade; only followed |

The set is closed by a falsifiable principle: **a new kind must exhibit a law of revision
distinct from all four.** Every candidate in the V2 noun inventory (snapshot, incident,
suggestion, PR, ADR, experiment, trail, report, cost, reasoning step, …) was tested and
reduced to these four with nothing left over (FORGE_KERNEL.md §4.7).

This discipline is named **Epistemic Typing** and is declared Forge's founding invention —
defined independently of Forge so future tools can adopt it on their own.

## Consequences

**Positive**
- Laws become attachable: immutability binds to Observation, the evidence requirement binds
  to Claim, accountability binds to Decision — physics per kind, not review checklists.
- Filing ambiguity dies: designed → Artifact; happened → Observation; believed → Claim;
  chosen → Decision. There is no fifth place, so there are no "where does this live?"
  debates, ever.
- The four kinds form the engineering loop (design → observe → interpret → decide), giving
  every object one lifecycle and the platform one story.

**Negative / trade-offs**
- Some records genuinely span kinds (a deployment is a decision *and* things observably
  happen); the model resolves this as multiple linked nodes, which is more nodes than a
  single "deployment row." Accepted: the linkage is information, not overhead.
- The closed set is a bet. If a fifth law of revision is ever demonstrated, admitting it is
  a constitutional amendment (Manifesto, Article X) — deliberately expensive.
