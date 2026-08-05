# Brok's Forge V2 — Master Plan

**Status:** Founding blueprint, pre-implementation. This document is the durable statement of
V2's intent and the source of truth for every V2 design decision, the same way
MASTER_ARCHITECTURE.md is for V1. V1 remains the shipped, stable product; nothing here changes
V1 behavior.

---

## 1. What V2 is

**An AI Engineering Operating System.** Not an observability platform, not an evaluation
dashboard, not a prompt manager. The single environment where an AI engineer designs, builds,
versions, simulates, evaluates, replays, debugs, explains, optimizes, deploys, observes, and
continuously improves production AI systems.

The analogy that defines the category: GitHub is where code lives, VS Code is where programming
happens, Kubernetes is where infrastructure runs — **Forge is where AI engineering happens.**

The one-sentence differentiation test: Langfuse shows you *what happened*. Forge tells you
*why it happened, what to do about it, what the evidence is, how confident it is — and remembers
the answer forever.*

## 2. Product doctrine (non-negotiable, inherited by every design)

1. **Engineering first.** Every screen exists to help an engineer ship a more reliable system.
2. **Everything explains itself.** No score, regression, or recommendation without a visible why.
3. **Every incident becomes knowledge.** Nothing is ever forgotten.
4. **Every failure becomes a regression test.** Automatically.
5. **Every deployment is reproducible.** Configuration-identical, from a pinned manifest —
   and behavioral drift under an identical configuration is detected, never assumed away.
6. **Every decision is replayable.**
7. **Every version is comparable.** Not just prompts — entire systems.
8. **Every suggestion carries evidence.**
9. **Every recommendation carries confidence.**
10. **The platform improves itself.** Nightly, without being asked.

## 3. The two primitives everything else is built on

> **Superseded in part by the conceptual foundation suite in [v2/](v2/README.md)**
> (constitution: [v2/MANIFESTO.md](v2/MANIFESTO.md), ADRs: [v2/adr/](v2/adr/README.md),
> canonical model: [v2/DOMAIN_MODEL.md](v2/DOMAIN_MODEL.md); derivation:
> [FORGE_KERNEL.md](FORGE_KERNEL.md)). The reduction there shows these are not primitives but
> derived structures over one deeper substrate: the Snapshot = `closure()` over the graph, the
> Ledger = the graph's own append log, the Envelope = a structural law on Claim nodes.
> Everything this section promises still holds. The delivery phasing in §8 is unchanged.

V2 is not 19 features. It is **two new primitives**, and every headline capability is a view
over them. This is the architectural insight that keeps V2 buildable by a small team.

### 3.1 The System Snapshot (the "Forge lockfile")

A content-addressed, immutable manifest that pins **every** component of an AI system at a
point in time: prompt version, model + parameters, retriever config, embedding model, memory
policy, planner, tool set, knowledge sources, guardrails, evaluation profile versions, dataset
versions, deployment target.

This single primitive *is*:
- **AI Git** — commit = snapshot; branch = mutable pointer to a snapshot; diff = structural
  comparison of two snapshots; rollback = repoint to an old snapshot; an "AI Pull Request" =
  a proposed snapshot plus the evaluation evidence comparing it to the current one.
- **Architecture Diff** — render the delta between two snapshots component-by-component.
- **Reproducible deployment** (doctrine 5) — a deployment references exactly one snapshot.
- **Replayability** (doctrine 6) — every recorded run stores the snapshot hash it ran under.
- **Agent DNA** — the *current* snapshot of an agent, rendered as an identity card: model,
  memory, knowledge, planner, retriever, prompt, tools, embeddings, guardrails, plus live
  overlays (health, cost, evaluations, deployments, ownership, dependencies).

V1 already does this in miniature: evaluation jobs pin dataset/prompt/profile versions at
creation. V2 generalizes that pattern from "pin 3 things on a job" to "pin everything, always,
as a first-class object."

### 3.2 The Evidence Ledger

An append-only event store where every engineering fact lands as an immutable, linkable record:
evaluation results, regressions, production incidents, root-cause findings, fixes applied,
deployments, suggestion outcomes (accepted/rejected/result). Every record carries: what
happened, which snapshot it happened under, which components were implicated, and links to
related records.

This single primitive *is*:
- **Engineering Memory** — "we've seen this before" = similarity search over the ledger,
  surfacing the previous fix, who applied it, its success rate, and confidence. V1's knowledge
  graph with its `occurrence_count` learning seam is the embryo of this; V2 makes it
  instance-level (this incident, this fix, this outcome), not just pattern-level.
- **Production Learning** — the pipeline that turns ledger entries into artifacts: failure →
  auto-generated regression test → suggestion → (if accepted) new snapshot.
- **Nightly Engineering Report** — a rendering of the last 24h of ledger activity plus the
  autonomous pass's findings.
- **Auditability** — every claim Forge ever makes cites ledger records as its evidence.

### 3.3 The Explanation Envelope (a schema, not a feature)

Doctrine rules 2, 8, and 9 become a literal API contract. Every derived claim the platform
emits — score, regression verdict, root cause, suggestion — is wrapped in:

```
{
  claim:        what Forge believes,
  method:       how it was computed (deterministic analyzer, statistical test, LLM judge — named),
  evidence:     ledger record references (never prose-only),
  confidence:   0–1 with the basis stated (sample size, agreement, historical precision),
  action:       recommended next step, if any, with expected improvement and its basis
}
```

No V2 surface may display a number this envelope can't back. This is machine-checked at the
API layer (a response type, not a convention), the same way V1 machine-checks its error
contract.

## 4. The lifecycle, mapped to modules

```
Design → Build → Version → Simulate → Stress Test → Evaluate → Replay → Debug → Explain
→ Optimize → Verify → Deploy → Observe → Learn → Generate New Tests → Improve → repeat
```

Every V2 module claims a segment. Nothing ships that doesn't sit on this loop.

| Lifecycle segment | V2 module | Built on | V1 seed |
|---|---|---|---|
| Design/Build/Version | Component Registries + Snapshots | Primitive 3.1 | agent/prompt/dataset/profile versioning |
| Simulate/Stress Test | Synthetic Evaluation Generator | LLM-generated suites from docs/API specs/prompts | Benchmark Gallery templates |
| Evaluate | Evaluation Engine (async, queued) | V1 executor behind its queue-ready seam | `EvaluationJobExecutor` |
| Replay/Debug | Interactive Agent Timeline | `TraceRecorder` SPI, finally wired | AI Debugger's honest stage timeline |
| Explain | Root Cause Explorer | Explanation Envelope over Failure Graph | `RootCauseEngine` |
| Optimize | AI Suggestion Engine | Envelope + ledger + snapshot diffs | Advisor (computed-on-read) |
| Verify/Deploy | Deployment Registry | Snapshots as deploy units, verify-before-promote | — (new) |
| Observe | Production Registry + Failure Graph | Live traces tagged with snapshot hash | correlation IDs, Prometheus |
| Learn/Generate/Improve | Engineering Memory + Autonomous Engineering | Evidence Ledger | knowledge graph + occurrence counter |

**Component registries** (prompt, model, memory, tool, retriever, planner, embedding,
knowledge, guardrail, evaluation, deployment, experiment) are **one generic versioned-component
substrate with typed views** — not twelve separately-built modules. V1's text-backed-enum
convention already makes component types a code-only addition; V2 doubles down: one table
family, one versioning behavior, one API shape, twelve (and later N) registered types.

## 5. Signature capabilities — what makes it unmistakably Forge

**Root Cause Explorer** (the flagship). Never "Evaluation Failed." Always: why, confidence,
evidence, affected components, related deployments, similar historical incidents (from the
ledger), estimated fix, expected improvement, one-click recommended action. V1's deterministic
`RootCauseEngine` stays the trustworthy core; V2 adds the historical-memory layer and the
component-attribution layer (Failure Graph).

**Failure Graph.** Not a trace waterfall — an attribution graph. Which component *originated*
the failure (planning, memory, retriever, prompt, tool, model, knowledge, guardrail) and how it
propagated. Extends V1's `ExecutionStage` vocabulary from a linear timeline into a causal graph.

**Interactive Agent Timeline.** Replay any recorded run: token-by-token, tool-by-tool,
decision-by-decision. Scrub, pause on a step, inspect state, branch into a what-if from any
point (re-execute the remainder under a modified snapshot). Requires the tracing seam wired for
real — this is the V2 work that unlocks the most, and V1 deliberately left the `TraceRecorder`
SPI as its drop-in point.

**Engineering Memory.** On any new incident: "We've seen this before — 3 times. The fix that
worked (2/3): rolling back the embedding model change. Applied by <engineer>, confidence 0.78,
evidence: <ledger refs>."

**Autonomous Engineering.** A nightly pass that: re-runs regression suites against current
snapshots, generates tests from yesterday's production failures, flags architecture smells
(duplicate prompts, dead tools, unused knowledge sources, cheaper-model opportunities), and
files each finding as a suggestion with an envelope. Morning output: the Nightly Engineering
Report. Engineers wake up to findings, not raw data.

## 6. The workspace (UX doctrine)

Linear's speed, VS Code's layout discipline, Raycast's command palette, GitHub's
object-linking. Concretely:

- **Command palette is the primary navigation** (⌘K). Every object, every action, reachable by
  keyboard. Clicks are the fallback, not the design center.
- **Everything is a linkable object** with a stable URL: snapshot, run, incident, suggestion,
  component version. Any claim's evidence is a link, never a dead-end tooltip.
- **Inspector-style layout**: list → detail → inspector panel, with split views and dockable
  panels for timeline/diff work. Density over whitespace; dark-first; zero marketing chrome.
- **Context-aware actions**: every object surfaces its 2–3 next engineering actions (compare,
  replay, roll back, promote, generate tests) where the eyes already are.

## 7. What V2 explicitly is not

- Not a chat product. No conversational assistant as the primary interface.
- Not a Langfuse/DeepEval/Promptfoo clone. Study them for principles; if a design could be
  mistaken for any of them, it fails review and gets redesigned (the Success Test).
- Not executive dashboards. If a screen's primary consumer isn't a hands-on engineer, cut it.
- Not black-box AI magic. Deterministic analyzers first; LLM-powered analysis is allowed but
  must declare itself in the envelope's `method` and never be the sole evidence.

## 8. Delivery phasing (each phase shippable, additive — V1 rules apply)

**V2.0 — The Substrate.** Generic component-registry substrate; System Snapshots; snapshots
pinned on every evaluation/deployment; Explanation Envelope enforced on advisor + root-cause +
regression outputs; Evidence Ledger recording evals/regressions/deployments. *Ships value:
Architecture Diff + full-system rollback.*

**V2.1 — Replay.** `TraceRecorder` wired to a real backend; Interactive Agent Timeline (replay
+ scrub + inspect); Failure Graph attribution over recorded traces. *Ships value: the debugger.*

**V2.2 — Memory & Suggestions.** Instance-level Engineering Memory over the ledger
("seen-before" matching); AI Suggestion Engine with one-click apply (apply = new snapshot,
never in-place mutation); failure → regression-test auto-generation. *Ships value: the platform
starts learning.*

**V2.3 — Autonomy.** Synthetic Evaluation Generator; nightly autonomous pass; Nightly
Engineering Report; production-traffic learning loop. *Ships value: Forge works while you
sleep.*

Each phase honors V1's engineering rules: additive migrations, published-service module
boundaries, ADR per significant decision, no phase breaks a prior phase's behavior.

## 9. The success test (applied to every screen before it ships)

- Would a senior AI engineer say *"this saves me hours every week"*?
- Would a staff engineer retire two other tools because of it?
- Would a CTO trust production deploys through it?
- Could anyone mistake it for Langfuse? **If yes — reject and redesign.**

The first-open reaction V2 is designed for: *"This feels like the operating system AI
engineers have been waiting for."*
