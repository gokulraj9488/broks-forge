# Forge Experience Platform — Experience Model

**Deliverable 1.** Derived before implementation. The Forge Experience Platform (FXP) proves that
the frozen platform — Kernel, Knowledge System, FVCS, FKGE — solves real AI-engineering problems
through coherent experiences. We do **not** begin with screens. We begin with what an AI engineer
actually does, and map each act onto a platform capability. If an act does not map, that is a
platform gap to be filed as an amendment — never worked around in an application.

> **The law of this phase:** applications *orchestrate*; the platform *reasons*. An FXP component
> holds no engineering truth of its own. It reads and writes through public APIs, and every answer
> it shows is one the platform can independently prove and reproduce.

---

## 1. What does an AI engineer do every day?

Stripped to fundamentals, the daily loop is:

1. **Change something** — a prompt, a model binding, a tool, a dataset, an agent, a policy.
2. **Try it** — run it, evaluate it, benchmark it; observe reality.
3. **Form a belief** — "this is better", "this regresses latency", with a confidence and evidence.
4. **Decide** — approve, promote, roll back; an act of will citing the beliefs.
5. **Ship** — deploy to an environment.
6. **Answer for it** — later, explain *why* the system is the way it is: to a teammate, an
   incident review, an auditor, an executive.

Every one of these is already a first-class platform concept:

| Engineer's act | Platform concept | Layer |
|----------------|------------------|-------|
| change an artifact | new `Artifact` revision; new **version/commit** | Knowledge + FVCS |
| run / evaluate | `Observation` (`Run`, `Incident`, `HumanFeedback`) | Knowledge |
| form a belief | `Claim` (statement, method, confidence, evidence) — Law 5 | Knowledge |
| decide | `Decision` citing claims or a judgment-call — Law 6 | Knowledge |
| ship | `Deployment` decision → `Environment`; **promotion**/**tag** | Knowledge + FVCS |
| answer for it | **provenance / explanation / impact / root-cause / confidence** | FKGE |

The experiences are therefore not invented — they are the **natural verbs of the platform, given a
surface**.

## 2. The seven questions an engineer asks

Every workflow is a sequence of these, and each maps to exactly one platform capability:

| Question | Capability | Where |
|----------|-----------|-------|
| *What is needed?* (browse the state) | typed read | `KnowledgeView` |
| *Where did this come from?* | provenance / lineage | FKGE |
| *What happens if I change it?* | impact / blast radius | FKGE |
| *Why is this believed?* | evidence traversal + confidence | FKGE |
| *Why was this done?* | decision explanation | FKGE |
| *What changed between versions?* | semantic diff | FVCS |
| *What caused this failure?* | root cause | FKGE |

An experience that cannot answer its question by composing these is not an FXP experience — it is a
platform amendment request.

## 3. The four experiences (derived, not chosen)

The daily loop (§1) splits cleanly into four surfaces by **mode of work**, not by screen:

- **Forge Studio** — the *authoring* mode. Create artifacts, record observations, author claims,
  record decisions, cut versions. It is the write side: every keystroke becomes a kernel fact with
  an actor, a time, and a hash. Studio also *reads* (browse, navigate versions, explain) because an
  author needs context, but its defining act is the lawful append.
- **Forge Explorer** — the *understanding* mode. Provenance, dependency, impact, lineage, root
  cause, confidence, evidence. It is a pure projection of FKGE — it writes nothing.
- **Forge Review** — the *judgement* mode. Review commits, evaluations, decisions; approve policies
  and deployments; read semantic diffs. It reuses the frozen AI-PR decision triad
  (`proposes`/`approves`/`rejects`) — review *is* recording decisions about decisions.
- **Forge Copilot** — the *conversation* mode. Natural-language questions answered by grounding
  through FKGE. **The LLM explains; FKGE proves.** The Copilot never originates engineering truth;
  it phrases a proof the platform computed, and every answer ships with that proof attached.

These four are exhaustive over the loop: you either **write** truth (Studio), **understand** it
(Explorer), **judge** it (Review), or **ask about** it (Copilot). Anything else is a composition of
these.

## 4. The delivery surfaces (one conceptual API, many bindings)

The four experiences are exposed through surfaces that differ only in *ergonomics*, never in
*capability*:

- **Conceptual API** (`ForgeClient`) — the single canonical API. Implemented in Java (the
  platform's language) and **the definition every other binding mirrors**.
- **CLI** (`forge …`) — the conceptual API for terminals and CI.
- **SDKs** (Java implemented; Python, TypeScript specified) — the conceptual API for programs.
- **REST API** — the conceptual API for the network and the (future) web frontend.

Because there is exactly one conceptual API, the surfaces cannot drift: the CLI, SDKs, and REST are
projections of `ForgeClient`, and `ForgeClient` is a thin orchestration of the platform. There is no
place for hidden logic to accrete.

## 5. The seven experience principles, made operational

The mission's principles are not slogans here — each is *enforced by construction* because the
platform enforces it:

| Principle | How FXP guarantees it |
|-----------|------------------------|
| **Deterministic** | every read is an FKGE/FVCS call, each a pure function of a log prefix |
| **Explainable** | every knowledge answer carries an FKGE proof object (steps → axioms) |
| **Reproducible** | every answer carries the `LogPosition` (`asOf`) it was computed at |
| **Evidence-backed** | claims carry evidence (Law 5); the Copilot refuses ungrounded answers |
| **History-aware** | every subject has a commit history via FVCS; `asOf` time-travels |
| **Version-aware** | Studio writes create versions; Review diffs them; Explorer reasons across them |
| **Minimal** | FXP adds no engineering logic — it orchestrates; duplicated logic is a defect |

> **"No feature may bypass the platform"** is the single acceptance test for every FXP feature: if a
> feature computes an engineering answer without the platform, it is rejected — regardless of how
> convenient it is.

## 6. The Copilot grounding contract (the hard one)

The Copilot is where "applications don't invent truth" is most at risk, so it is specified as a
contract, not a hope:

1. A question is resolved to a **subject** (a `NodeId`) and an **intent** (why / provenance / impact
   / root-cause / confidence / evidence).
2. FKGE computes the **proof** for that intent — deterministically, from the log.
3. If the proof is empty (no evidence, unknown subject), the Copilot **refuses** — it returns "I
   cannot answer this from the platform" and never calls the language model to fill the gap.
4. Only a non-empty proof is handed to the language model, whose sole job is to **narrate** it.
5. The `GroundedAnswer` always carries the machine-checkable proof, so any prose can be verified
   against the facts. The narration is presentation; the proof is truth.

This makes the LLM structurally incapable of inventing engineering truth in FXP: it never sees the
graph, only a proof, and it is never invoked without one.

## 7. What FXP is *not*

- Not a place where engineering logic lives — it holds none; it composes the platform's.
- Not a second source of truth — it stores nothing; the kernel log is the only record.
- Not a platform modifier — it consumes public APIs and adds no kind, family, ontology, or lens
  requiring a frozen-layer change. A need to do so is a **stop-and-file-an-amendment** event.
- Not an integration owner — integrations are **adapters** at the edge; the platform never depends
  on them.
