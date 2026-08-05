# Forge Knowledge Graph Engine — Graph Reasoning Theory

**Deliverable 1.** Derived before implementation, from the frozen Forge Kernel, Forge
Knowledge System, and Forge Version Control System. No graph-database semantics are assumed;
every capability is derived from the primitives those three layers already expose.

> The graph already exists. The kernel stores nodes and typed edges; the knowledge system
> gives them meaning; FVCS versions them. **Phase 4 does not store anything. It builds
> deterministic *reasoning* over what is already stored** — it turns engineering *knowledge*
> into engineering *understanding*.

---

## 0. The stance: understanding is entailment, not more data

The frozen kernel records four kinds of fact — **Artifact** (intent), **Observation**
(reality), **Claim** (belief), **Decision** (will) — connected by five edge families with
*fixed meaning*: **composition** (made-of), **derivation** (made-from), **evidence**
(believed-because), **causality** (brought-about), **intent** (done-for).

Because each edge family has a fixed meaning, **traversing edges of a chosen family in a
chosen direction is an act of inference** whose only inference rule is "follow a `because`."
Every conclusion FKGE produces is therefore a *citation of stored facts*, never a new fact.

> **Understanding = the structure the graph entails but does not store explicitly** — the
> closures, paths, and projections that follow necessarily from the recorded facts and the
> fixed semantics of the edge families.

This yields the governing constraint of the entire engine:

> **FKGE only reads. It never appends.** Understanding is a *view*, never a write. An engine
> that had to write a fact to answer a question would be admitting the graph did not already
> contain the answer — which would be a knowledge-system defect to be filed as an amendment,
> not worked around here.

---

## 1. First principles

Each concept below is *defined* in terms of the frozen primitives, then reduced to a
traversal or an arithmetic over recorded values. Nothing is heuristic; nothing is learned.

### 1.1 What is engineering understanding?
Not the accumulation of facts — the ability to answer **why**, **what-if**, and
**what-depends-on** about the engineered system, with every answer grounded in recorded fact.
Formally, understanding of a subject *s* is the set of questions about *s* that the graph
*entails* under the fixed edge semantics: the backward and forward closures of *s* and the
paths within them, projected into a form an engineer reasons with. The graph holds *what is*;
understanding is the *derivable* structure over it.

### 1.2 What is explanation?
An explanation of a node *N* is a **finite, acyclic, ordered walk** from *N* back to the facts
that justify it, following only the edge families that license "because" *for the question
asked*:
- *Why does this exist / have this shape?* → **composition ∪ derivation**.
- *Why is this believed?* → **evidence** (Claim → its Observations/Artifacts).
- *Why was this done?* → **intent** (Decision → the Claims it cites) + the recorded
  judgment-call.
- *Why did this happen?* → **causality** (event → the event/decision that caused it).

An explanation is **complete** iff every leaf is a proper *axiom*: a primary **Observation**
of reality, an **Artifact** with no further derivation, or an explicit **judgment-call** (a
Decision that cites no prior claim — the terminal "because a human decided"). An explanation
is a **proof tree whose axioms are observations and judgment-calls**. If a leaf is none of
these, the explanation is *incomplete*, and it must **say so and name the gap** — never fall
silent.

### 1.3 What is provenance?
Provenance of *N* is its **complete derivation history**: the transitive closure of *N* in the
**backward** direction under **derivation ∪ composition ∪ intent ∪ evidence**, bounded by the
kernel's causal order (nothing in *N*'s provenance may have a `LogPosition` after *N*).
Provenance answers *where did this come from, through what, decided by whom, on what evidence*.

Because the kernel is content-addressed and hash-chained, provenance is a **Merkle proof**:
*N*'s closure hash **certifies** its provenance — change any ancestor and *N*'s hash changes.
Provenance is thus not merely reconstructable but **verifiable**.

> **Closure vs provenance.** *Closure* is the kernel-level set of revisions *N* structurally
> depends on (its reproducibility set) — the *what*. *Provenance* is the semantically-typed,
> causally-ordered explanation of that closure — the *what + how + why + who*.

### 1.4 What is impact?
Impact of *N* is the **dual of provenance**: the transitive closure in the **forward**
direction — everything that has *N* in *its* provenance. *If N is wrong, changes, or is
removed, what is affected?* **Blast radius** is the impact set; **critical path** is the most
load-bearing chain within it.

> **Duality law:** `X ∈ impact(N) ⟺ N ∈ provenance(X)`.

Provenance and impact are **one relation read in two directions**. A single traversal engine
serves both, and their mutual consistency is guaranteed *by construction*, not by test.

### 1.5 What is dependency?
Dependency is the **structural subset of provenance**: *N* depends on *M* iff *N* cannot be
**reproduced** without *M* — i.e. *M* is in *N*'s kernel closure, following the
**reproduction-bearing families composition ∪ derivation only**. Evidence and intent *explain*
but are not always required to *rebuild*. Hence `dependency ⊆ provenance`. Dependency is *what
you must have to rebuild*; provenance is *what you must cite to justify*. They are kept
separate because "what do I need to run this" ≠ "why do I trust this."

### 1.6 What is causality?
Causality is the edge family the kernel reserves for **events**: an Observation/Decision → the
Observation/Decision it brought about. Causality is **not** derivation ("made-from"); it is
"brought-about." Root-cause analysis walks causality **backward**. Crucially, the kernel's
`LogPosition` is a **causal clock**: a genuine cause must *precede* its effect in the per-org
total order. FKGE **enforces** this — a causal edge that violates `LogPosition` monotonicity
is a data defect, not a valid explanation. Causality is therefore **checkable, not asserted**.

### 1.7 What is confidence?
Confidence is a bounded scalar **[0,1]** that, by Law 5, lives **only on Claims**. The other
kinds are not truth-bearers that carry confidence:
- **Observation** = reality recorded → confidence `1` by definition.
- **Artifact** = intent → not a truth-bearer, no confidence.
- **Decision** = will → neither true nor false; it *cites* claims, which carry the confidence.

**Propagation is conservative and assumption-free.** A conclusion resting on several claims is
at most as strong as its **weakest** supporting claim:

> `confidence(conclusion) = min over supporting claims of confidence(claim)`  (a
> conjunction is as strong as its weakest link).

`min` is chosen because it assumes **nothing**. The product rule (`∏ cᵢ`) would assume
statistical **independence** of the claims — an unstated heuristic, and thus forbidden by the
"no hidden heuristics / no probabilistic reasoning" mandate. FKGE never *invents* a confidence
and never *raises* one by traversal; it only takes the tight lower bound of recorded numbers.
Confidence handling is **arithmetic on recorded values**, not inference of new ones.

### 1.8 What is traceability?
Traceability is the **existence and exhibition of a path** between two nodes under a specified
family — the property that any node connects back to its justifying facts and forward to its
dependents, *with the path itself as the deliverable*. A system is **fully traceable** iff no
Claim lacks an evidence path and no Decision lacks either a cited claim or a judgment-call —
exactly what the kernel's **Laws 5 & 6 now enforce at append**. So for law-abiding data,
traceability holds *by construction*; FKGE's job is to **exhibit** the path (`trace(from, to,
families)`), or to prove none exists.

### 1.9 What is influence?
Influence is the **structural, transitive** form of impact: not merely *is X reachable from
N* but *how load-bearing is N to X*. Influence of *N* = a deterministic function of how much of
the system rests on it: the count of distinct reachable dependents and its membership in
critical paths. The **influence graph** ranks nodes by this exact structural measure. We
**reject** probabilistic centrality (PageRank/eigenvector): its damping factor is a heuristic,
its computation is an iterative non-closed-form approximation, and it conflates edge
directions. Influence here is **counted, not estimated**.

### 1.10 What is uncertainty?
Uncertainty is the **complement of confidence and traceability** — the *absence or weakness of
grounding*. FKGE **locates** it; it never *estimates* it. Three deterministic kinds:
1. **Epistemic** — a Claim with low confidence, or a conclusion whose propagated `min` falls
   below a caller-supplied threshold.
2. **Structural (incompleteness)** — a node that *should* be grounded but isn't: a Claim whose
   evidence walk hits an unresolved frontier, or a Decision that is a bare judgment-call.
3. **Temporal** — the graph is bitemporal; a fact may be superseded. Uncertainty includes *was
   this true as-of the time asked?*, resolved deterministically by `resolveAt`.

Uncertainty is reported as a **diagnostic that points at the exact claim, missing edge, or
judgment-call** responsible — never as an opaque probability.

---

## 2. The reproducibility theorem (the reasoning stance)

> **Theorem.** Every FKGE result is a deterministic function of an immutable log prefix.

*Proof sketch.* (a) The kernel log is append-only and content-addressed, so a `LogPosition`
names an immutable prefix. (b) Every FKGE algorithm is pure graph traversal plus arithmetic
over recorded values, with all sets and tie-breaks ordered by the total order `(LogPosition,
RevisionHash)`. (c) Every query is evaluated **as-of** a position (default: latest). Therefore
the same query over the same prefix yields byte-identical results, forever. ∎

**Corollaries.**
- **FKGE is a pure projection layer. It stores nothing.** Any index it builds is a
  deterministic function of the log and is fully discardable — so there is *no hidden mutable
  state* to corrupt a result. (This pre-empts the adversarial "hidden mutable state" attack.)
- An FKGE **answer can carry the `LogPosition` it was computed at**, making it a citable,
  independently re-verifiable object — the reasoning analogue of FVCS's reproducibility
  certificate, lifted from *state* to *answers*.

**Determinism discipline (global invariant).** Whenever an algorithm returns a set or must
break a tie, it orders by **`(LogPosition, RevisionHash)`** — a total order the kernel
provides. No algorithm depends on hash-map iteration order, wall-clock, or randomness.

---

## 3. Query theory — a closed algebra of six primitives

Questions are posed in **engineering language**, not graph language. A **Question** is
`(subject node(s), relation-lens, direction, depth-bound, as-of position)`. Answering =
choosing the lens and direction, traversing, and projecting.

These six primitive operations form the **complete basis** from which every engineering
question in the mission is composed:

| # | Primitive | Meaning | Kernel/knowledge/FVCS basis |
|---|-----------|---------|------------------------------|
| 1 | `resolve(ref, asOf)` | a node's state as-of a position | kernel resolve / resolveAt |
| 2 | `neighbors(node, family, dir)` | one typed step (the atom) | kernel typed references |
| 3 | `closure(node, families, dir, asOf)` | transitive neighbors = reachability | repeated `neighbors` |
| 4 | `path(from, to, families, dir)` | a connecting walk | traversal with predecessor map |
| 5 | `project(nodeset‖paths, shape)` | fold into an engineering answer | knowledge-system typing |
| 6 | `fold(logRange, accumulator)` | deterministic aggregation over the log | kernel log + FVCS history |

**Closure claim.** Provenance, impact, and dependency are each an instance of `closure` with a
chosen family-set and direction; explanation and traceability are `path`; summarization,
ranking, and verdicts are `project`; history and confidence aggregation are `fold`. The
algebra is **closed, decidable, and terminating**: expressible power is exactly transitive
closures and paths over a fixed finite set of typed relations on a finite graph.

**Every mission question, decomposed:**

| Engineering question | Composition |
|----------------------|-------------|
| "Why was this deployment approved?" | `path(decision, {intent,evidence}, backward)` → `project(explanation)` |
| "What evidence supports this claim?" | `closure(claim, {evidence}, backward)` filtered to Observations/Artifacts |
| "What decisions caused this incident?" | `closure(incident, {causality}, backward)` filtered to Decisions |
| "What changed between these versions?" | **FVCS `diff`** over two snapshots (reused, not reinvented) |
| "What systems depend on this dataset?" | `closure(dataset, {composition,derivation}, forward)` filtered to systems |
| "What experiments justify this benchmark?" | `closure(benchmarkClaim, {evidence}, backward)` filtered to Runs |
| "What is the complete provenance of this model?" | `closure(model, provenanceFamilies, backward)` → `project(lineage)` |
| "Explain this workflow." | `project(closure(workflow, {composition}, downward), summary)` |

---

## 4. Models (summarized here; each has its own deliverable document)

- **Explanation model** → [EXPLANATION-MODEL.md](EXPLANATION-MODEL.md). An `Explanation` is a
  proof tree: `(root, ordered steps, leaves classified {Observation | primary-Artifact |
  judgment-call | frontier-unresolved}, as-of, completeness verdict)`. Built by backward typed
  traversal with a visited-set (termination + acyclicity), ordered by `(LogPosition,
  RevisionHash)`.
- **Provenance model** → [PROVENANCE-MODEL.md](PROVENANCE-MODEL.md). Backward closure under
  `{composition, derivation, evidence, intent}`, causally ordered, with the closure hash as a
  verifiable certificate.
- **Dependency model** → [DEPENDENCY-MODEL.md](DEPENDENCY-MODEL.md). Backward closure under
  `{composition, derivation}` (reproduction-bearing only), topologically ordered; critical
  path = longest chain; blast radius = the forward dual.
- **Reasoning algorithms** → [REASONING-ALGORITHMS.md](REASONING-ALGORITHMS.md). The full set,
  each with its determinism and termination argument.
- **Query language** → [QUERY-LANGUAGE.md](QUERY-LANGUAGE.md). The engineering-question surface
  over the six primitives.

---

## 5. Capabilities — derived, constrained, or rejected

Every capability the mission lists is admitted **only if it reduces to the six primitives with
no heuristic and no learned parameter**.

**Admitted (each reduces to closure/path/project/fold):** provenance analysis, root-cause
analysis, impact analysis, dependency analysis, lineage reconstruction, evidence traversal,
decision explanation, confidence propagation (as the `min`-bound), reachability, closure
exploration, semantic neighborhood, blast-radius, critical-path discovery, influence (as
structural rank), knowledge summarization (as deterministic projection), system explanation.

**Admitted only in a constrained, deterministic form:**
- **Similarity search & pattern detection.** Admitted **only as structural** similarity: two
  nodes are similar iff they share an `ObjectType` and have **isomorphic typed neighborhoods**,
  compared by a *neighborhood signature* — a deterministic hash of the sorted multiset of
  `(family, direction, neighbor ObjectType)` triples. Pattern detection = recurring
  signatures. **Embeddings / vector similarity are rejected**: they are learned, opaque, and
  non-reproducible.

**Rejected outright (cannot be justified from first principles under the mandate):** anything
requiring learned parameters, statistical inference, probabilistic centrality with a damping
factor, or any ranking that is not a closed-form function of the log. These would violate
determinism, reproducibility, or the "no hidden heuristics" rule.

---

## 6. What FKGE is *not*

- **Not another graph database.** The graph is already stored by the kernel. FKGE adds no
  storage, no second copy, no write path.
- **Not a query planner over arbitrary schemas.** Its algebra is fixed to the five kernel edge
  families and the knowledge-system types — that fixedness is what makes every answer a
  citation.
- **Not probabilistic.** No inference of new numbers; only traversal and bounded arithmetic on
  recorded ones.
- **Not Phase 5.** No UI, dashboards, visual editors, AI assistants, or applications — only
  deterministic graph reasoning, consumed through a public API and extended through an SPI.

---

## 7. Canonical vocabulary

| Term | Definition |
|------|------------|
| **Understanding** | the structure entailed by the graph but not explicitly stored |
| **Explanation** | a finite acyclic proof-tree walk to axioms (observations / judgment-calls) |
| **Provenance** | backward closure under derivation∪composition∪intent∪evidence, certified by the closure hash |
| **Dependency** | backward closure under composition∪derivation (reproduction-bearing only) |
| **Impact / blast radius** | forward dual of provenance |
| **Critical path** | the longest / most load-bearing chain in an impact or dependency closure |
| **Causality trace** | backward closure under the causality family, LogPosition-monotone |
| **Confidence** | recorded scalar on Claims; propagated as the `min` bound |
| **Traceability** | existence + exhibition of a typed path between two nodes |
| **Influence** | structural rank by reachable-dependent count + critical-path membership |
| **Uncertainty** | located weakness of grounding: epistemic, structural, or temporal |
| **As-of position** | the `LogPosition` a query is evaluated at (default latest) |
| **Neighborhood signature** | deterministic hash of a node's typed one-step neighborhood |
| **Answer** | a projection carrying the `LogPosition` at which it was computed |
