# Evolution

Evolution answers two questions about any artifact: **where did this come from**, and **what would I
break if I changed it**.

## Lineage and blast radius

```
        WHERE IT CAME FROM                      WHAT IT INFLUENCES
        (dependencies)                          (dependents)

        Provider ──┐                         ┌── Evaluation A
        Dataset  ──┼──►  THIS ARTIFACT  ──────┼── Evaluation B
        Prompt   ──┘                         └── Benchmark C
                                                      │
                                              transitive impact:
                                              everything downstream
                                              of those
```

Before you change something, you should know what it will affect. Evolution makes that explicit
rather than leaving it to memory — and memory is exactly what fails when a dataset is regenerated
three months after anyone touched it.

## What the tab holds

**Verdict banner** — impact before inventory. The first thing you see is what a change here would
mean, not a list of rows.

**Where it came from** — the artifact's dependencies.

**What it influences** — its dependents, and the transitive impact count.

**Deployment timeline (AI Git)** — every revision, which is promoted, rollback readiness, and the
recorded rationale for each change. See [AI Git](/docs/ai-git).

**How it changed** — the historical revisions.

**Evidence** — the evaluations that bear on this artifact.

## Where the relationships come from

They are **real**, not declared. The graph is built from actual references: an evaluation that pins
a prompt genuinely depends on it; an agent configured with a provider genuinely reaches it. Nobody
maintains a dependency file, so nothing can be out of date.

## Impact as a number that means something

The impact count appears throughout the platform — in Brok answers, in investigations, in Evolution
itself — and it is always accompanied by a sentence saying what it means:

> *"Promoting it changes what 3 downstream artifacts depend on."*
> *"Nothing downstream depends on this artifact, so the promotion is contained."*

A bare number invites misreading. A number with its consequence stated is engineering information.

## Questions Evolution answers

- Can I safely regenerate this dataset? — check the dependents.
- Why does this evaluation exist? — check its dependencies.
- Is this prompt still used anywhere? — an artifact with no dependents may be dead weight.
- What is the blast radius of this change? — the transitive impact.
- Is production running the newest revision? — the deployment timeline.

Brok reaches the same reading with *"Show every artifact affected by X"* and
*"What changed between these revisions?"*.

See also: [AI Git](/docs/ai-git) · [Forge Graph](/docs/forge-graph) · [Registry](/docs/registry)
