# V2-0003. The Claim Law (the Explanation Envelope as physics)

- Status: Accepted
- Date: 2026-07-28
- Level: Conceptual (no implementation content)

## Context

The V2 doctrine demands that nothing in Forge produce unexplained output: every score,
recommendation, regression verdict, and anomaly must carry its method, evidence, confidence,
and recommended action. The original master plan expressed this as the "Explanation
Envelope" — a response schema wrapped around derived outputs.

A schema is the wrong strength of guarantee. Schemas are applied at boundaries by code that
can be bypassed, forgotten, or special-cased under deadline pressure. A constitution-level
promise ("no unexplained number can exist") needs constitution-level enforcement.

## Alternatives considered

- **A response schema at the API boundary (the original Envelope).** Enforces shape at one
  boundary only; internal surfaces, exports, background jobs, and future modules can all leak
  naked numbers. The guarantee erodes precisely where nobody is looking.
- **A UI convention ("always show the why").** The weakest form; dies with the first
  redesign.
- **Post-hoc linting/auditing of stored outputs.** Detects violations after users have
  already seen the unexplained number; trust is already spent.
- **Mandatory human review of derived outputs.** Does not scale and reintroduces the
  subjectivity the law exists to remove.

## Decision

The Envelope becomes a **structural law on the Claim kind**: a Claim node is
*unappendable* without —

1. **evidence** — references to the observations (or prior claims) it rests on; never
   prose-only;
2. **method** — the named procedure that produced it (deterministic analyzer, statistical
   test, LLM judge — declared, so LLM-derived beliefs can never masquerade as measurements);
3. **confidence** — a calibrated value with its basis stated.

An optional **action** (recommended next step with expected improvement) makes a claim a
suggestion.

Combined with the rule that **every derived number is a claim**, the promise becomes
airtight by construction: an unexplained number is not forbidden in Forge — it is
*unrepresentable*, the way Git cannot store a commit without a tree.

Calibration is kept honest by the closed learning loop (ADR-V2-0008): when a claim's action
is adopted and later observations confirm or refute the expected result, the producing
method's confidence calibration is adjusted. Uncalibrated confidence is worse than none;
this loop is therefore part of the law's definition, not an enhancement.

## Consequences

**Positive**
- "Why?" always has an answer that is a traversal, not a text generation: follow the
  evidence edges or conclude the claim could never have existed.
- Every analytic surface, present and future, inherits explainability for free — including
  aggregates and KPIs, which are claims with `method: aggregation`.
- LLM-powered analysis is admissible but permanently honest: it signs its name, declares
  its method, and accumulates a public track record.

**Negative / trade-offs**
- Producing a claim costs more than printing a number; quick-and-dirty analytics get
  friction by design. Mitigation is tooling that assembles evidence automatically, never a
  relaxation of the law.
- Confidence theater is a real failure mode if calibration is deferred; hence the loop is
  constitutive (above), and methods without track records must say so.
