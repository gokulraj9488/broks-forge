# KN-0003. The definition/result split (and Cost as a non-object)

- Status: Accepted
- Phase: 2

## Context

Several candidate objects conflate a designed thing with a derived judgment. "Evaluation" names both the
spec you author and the score you get; "Experiment" names both the design and the conclusion; "Cost"
names both a measured charge and an aggregate.

## Decision

1. **Split definition from result by epistemic kind.** The *definition* is an **Artifact** (intent you
   design); the *result* is a **Claim** (belief you derive, under the Claim law). So: Evaluation
   (Artifact) → Evaluation Verdict (Claim); Experiment (Artifact) → Experiment Conclusion (Claim);
   Benchmark (Artifact) → Benchmark Score (Claim).
2. **Cost is not an object.** A measured cost is a field on a Run (Observation). An aggregate cost is a
   `cost-rollup` Claim (`method: aggregation`). Cost has no independent identity, lifecycle, or
   ownership.

## Consequences

- Results automatically inherit the Claim law (evidence + method + confidence): no evaluation score can
  exist without citing the runs it measured and naming its method. This is the "no naked numbers"
  guarantee applied to the entire measurement surface.
- The definition is reusable and versionable independently of its many results.
- The catalog shrinks: what looked like ~4 objects (Cost, plus three conflated pairs) becomes 3
  Artifact/Claim pairs and zero Cost object.
- Querying "the current score of X" is a traversal over Claims citing X's runs — never a mutable field.
