# The Canonical Domain Model of Brok's Forge V2

**Status:** Normative. This is the source of truth for every future module. Where any design,
document, or implementation disagrees with this model, this model wins or must be amended
first (Manifesto, Article X). It defines *what exists and how it behaves* — never how it is
stored, transported, or rendered.

Reading order: [MANIFESTO.md](MANIFESTO.md) (the constitution) → [adr/](adr/README.md) (why
each primitive exists) → this document (the precise model).

---

## 1. Identity

Two distinct notions of identity exist, and confusing them is the root of most versioning
bugs in conventional systems. This model separates them absolutely.

### 1.1 Continuant identity

A **continuant** is a thing with a biography: *the* support agent, *the* checkout prompt,
*the* nightly evaluation suite. A continuant has a stable, opaque identifier assigned at
first append and never reused. It is the subject sentences are about ("the retriever got
slower") and the anchor its revisions share.

### 1.2 Revision identity

A **revision** is one immutable state of a continuant. Its identity **is its content hash**,
computed over its content *including the revision hashes of everything it references*
(Merkle). Consequences, all normative:

- Two revisions with equal content are the same revision (deduplication is semantic, not an
  optimization).
- A revision can never change meaning: everything it references is pinned by hash.
- Equality of arbitrarily large structures is a hash comparison.
- The hash of a closure (§8.3) is a **reproducibility certificate**.

### 1.3 Addresses

Every node, edge, and revision has a stable, canonical address of the conceptual form:

```
forge:<organization>/<kind>/<continuant-id>            — the continuant (resolves to its current revision via its name)
forge:<organization>/<kind>/<continuant-id>@<revision> — one immutable revision
forge:<organization>/name/<path>                       — a name (§7)
```

Addresses are the universal currency: anything citable, shareable, subscribable, or
focusable is an address. There is no object in Forge without one.

### 1.4 The log position

Every append receives a monotonically increasing **log position** within its organization's
graph. Log position is the causal clock of the system; wall-clock times are attributes
(§2.5). Any query may be evaluated *as of* a log position, and the answer is deterministic
forever.

---

## 2. The universal append

Everything that ever happens in Forge is an **append**. An append carries, without
exception:

| Field | Meaning |
|---|---|
| payload | the node revision, edge assertion, edge retraction, name repointing, or annotation being added |
| actor | the signed identity that performed it (§6) — never absent, never anonymous |
| valid time | when the fact was true in the world |
| record time | when Forge recorded it |
| log position | assigned by the log, total order per organization |

An append is atomic and irrevocable. There is no update and no delete anywhere in the model;
their absence is Law 1, not an omission.

**Regulated-content erasure (Law 1, amended):** payload content subject to erasure law is
encrypted under per-subject keys at append time; erasure destroys the key. The node's
identity, hash, edges, and log position remain forever — the graph never lies about *that*
something happened — while the content becomes permanently unreadable. The destruction is
itself an append carrying actor, legal authority, and time. No engineering fact is deleted;
regulated content is destroyed, tombstoned, and attributed.

---

## 3. Entities: the four kinds

Every node is exactly one kind. Kind is fixed at first append and can never change (a
miskinded node is superseded by a correctly kinded one). Kinds are **closed**; subtypes
within each kind are **open**, registered as data (ADR-V2-0010).

### 3.1 Artifact — intent

A designed thing, authored by an actor.

- **Structure:** continuant + revision chain. Each revision carries its subtype, its content,
  and composition/derivation edges.
- **Revision law:** superseded by new revisions; never edited.
- **Open subtype registry (initial):** agent, prompt, model-configuration, retriever,
  embedding, memory-policy, planner, tool, knowledge-source, guardrail, policy, dataset,
  evaluation-profile, regression-test, subscription-program, lens-definition, subtype
  registration itself. (Yes: subscriptions, lenses, and the type registry are artifacts —
  the system's own machinery is versioned under its own laws.)
- **Closedness rule:** an artifact revision marked *executable* must have a fully pinned
  closure — no unresolved names in its transitive composition (§8.3).

### 3.2 Observation — reality

A recorded happening.

- **Structure:** immutable single node (no revision chain — reality has no versions), with
  subtype, content, the **closure hash it occurred under** (mandatory where an executing
  system was involved — Law 7), and edges to what it observed.
- **Revision law:** never revised, never superseded by argument. It may be **annotated**
  (appends attached to it) and **contradicted** only by another observation. An erroneous
  recording is marked `disputed` by annotation, never removed.
- **Open subtype registry (initial):** evaluation-result, trace, reasoning-step, tool-call,
  token-usage, latency, cost, incident, production-event, rollout-event, user-feedback,
  annotation, **trail** (ADR-V2-0009), calibration-outcome.
- **Granularity rule:** observations compose — a trace is an observation whose children
  (steps, tool calls) are observations, linked by composition edges. Fine granularity is
  always admissible; aggregation happens in claims and lenses, never by discarding detail.

### 3.3 Claim — belief

A derived interpretation. The only kind that can be *wrong*.

- **Structure (all four mandatory — Law 5, unappendable without):**
  1. **statement** — what is believed, typed by subtype;
  2. **method** — the named, versioned procedure that produced it (methods are artifacts);
  3. **evidence** — one or more evidence edges to observations or prior claims; never
     prose-only;
  4. **confidence** — a value in [0,1] with its stated basis (sample size, agreement,
     historical calibration of the method).
- **Optional:** **action** — a recommended next step with expected improvement and its
  basis. A claim with an action is a *suggestion*.
- **Revision law:** superseded by better reasoning or new evidence; additionally may be
  **confirmed** or **refuted** by later observations (§5), which feeds method calibration.
- **Open subtype registry (initial):** score, regression-verdict, root-cause, anomaly,
  seen-before-match, suggestion, expected-improvement, architecture-smell, aggregate
  (KPIs are claims with `method: aggregation` — no naked numbers anywhere, including
  analytics).
- **The rule of glass** (clarified per adversarial review, finding D2): a number you *look
  at* is an ephemeral traversal result and needs no claim; a number you *keep or cite* is a
  claim. Law 5 governs persistence and citation, not rendering — live dashboards are
  queries, not append storms.

### 3.4 Decision — will

A committed choice by an actor.

- **Structure:** the choice made; the **alternatives considered** (including "do nothing");
  the **basis** — cited claims via intent edges, *or* an explicit `judgment-call`
  self-declaration (Law 6); what it **produced** (possibly nothing); the log position of its
  making.
- **Revision law:** never unmade and never superseded — a decision that no longer stands is
  *followed* by a new decision that supersedes its *effect* (e.g., a rollback follows a
  deploy; both remain true forever).
- **Proposal protocol:** a decision may be appended as `proposed` by one actor and later
  bound by an `accepting` or `rejecting` decision of another — this three-append pattern
  *is* the AI Pull Request, approvals workflow, and review model. There is no separate
  workflow machinery.
- **Open subtype registry (initial):** adopt-revision, deploy (a name repointing — §7),
  rollback, approve, reject, defer, accept-risk, retire, architecture-decision (the ADR,
  native at last).

---

## 4. Relationships

### 4.1 The edge

An edge is a first-class fact: **addressable, immutable, actor-signed, bitemporal**. It
asserts that a typed relationship holds between two nodes over a validity interval. An edge
is retracted only by a later retraction append (which does not erase it — the edge, its
period of assertion, and its retraction are all permanent history). Because edges have
addresses, claims can cite edges as evidence and observations can dispute them: **every
relationship contains its own history and can itself be reasoned about.**

### 4.2 The five families

Every verb belongs to exactly one family; the family fixes the semantics that traversal,
closure, and law-checking rely on. Verbs are open (registered data); families are closed.

| Family | Semantics | Endpoint rule | Initial verbs |
|---|---|---|---|
| **Composition** | whole is built from part | Artifact→Artifact (intent); Observation→Observation (recorded structure) | uses, pins, member-of, step-of |
| **Derivation** | thing came from thing | same-kind, plus Artifact→Observation for generated-from | derived-from, supersedes, branch-of, generated-from, merged-from |
| **Evidence** | belief is justified by fact | Claim→Observation, Claim→Claim, Claim→Edge | cites, supports, refutes, measured-by |
| **Causality** | happening brought about happening | Observation→Observation; Claim *asserts* causality it cannot decree | caused, triggered, propagated-to, resolved-by |
| **Intent** | choice connects to its context | Decision→Claim, Decision→any (produced), Actor→Decision | based-on, considered, produced, decided-by, applied |

**The causality rule** (normative and subtle): causal edges between observations may only be
*asserted by claims* — a `caused` edge is appended together with the claim that carries its
evidence and confidence. Causality in Forge is always a justified belief, never an
unexplained fact. This single rule is what makes every root-cause graph trustworthy.

### 4.3 Cardinality and cycles

The model imposes no global cardinality limits. Two structural rules only: composition
edges within one artifact revision's closure must be acyclic (a system cannot contain
itself); derivation chains must be acyclic per continuant. Causality and evidence graphs
may be arbitrarily shaped — reality is not obliged to be a tree.

---

## 5. Events

There is no event catalogue separate from the model: **the event types are exactly the
append types**, and the log is the bus (ADR-V2-0008).

| Event (= append) | Emitted when |
|---|---|
| `node-appended(kind, subtype)` | any node of any kind is added |
| `edge-asserted(family, verb)` | a relationship is asserted |
| `edge-retracted` | a relationship is withdrawn |
| `name-repointed` | the only mutation in the system occurs (§7) |
| `annotation-appended` | an annotation attaches to any address |
| `clock-tick` | emitted by the substrate itself at a declared coarse granularity — the one observation stream the kernel produces, so scheduled behavior is a subscription like any other and time itself is in the record |

**Subscriptions** are standing traversal patterns over this stream, bound to programs
(themselves versioned artifacts), running as first-class actors under all laws. Normative
semantics:

- **Delivery:** a subscription observes every matching append exactly once, in log order.
- **Determinism:** re-running a subscription's program over a log range reproduces its
  appends (programs receive log position, never wall-clock or randomness — the same
  discipline that makes replay possible at all).
- **Budget and visibility:** every subscription declares an append budget per firing;
  cascades (appends triggering subscriptions whose appends trigger subscriptions) are
  permitted but fully visible as intent-edge chains, and budget exhaustion is itself an
  observation. Runaway autonomy is thereby impossible *silently*.
- **Calibration feedback** is a distinguished built-in subscription: when a decision adopts
  a claim's action and later observations bear on its expected improvement, a
  `calibration-outcome` observation is appended against the claim's method — closing the
  learning loop structurally (Law 5's honesty depends on it).

---

## 6. Actors and ownership

- An **actor** is a signed identity: a human engineer, a team, an external system, or a
  Forge program. Actors are nodes (so edges can reference them) but not a fifth kind — they
  are reified provenance.
- **Provenance is not ownership.** Every append has a provenance forever; no node has an
  owner, because owned facts invite edited facts. What has ownership semantics is the
  **name**: each name carries a **stewardship** — the set of actors entitled to repoint it.
  Repointing `prod` *is* deploy permission; the entire change-control model of the platform
  is stewardship of names, nothing else.
- **The organization** is the boundary of one graph: one log, one namespace, one membership.
  Cross-organization sharing, if it ever exists, is export/import of subgraphs — never a
  shared graph (deny-by-default at the tenant boundary is inherited from V1 as doctrine).
- **Read visibility** (added per adversarial review, finding C2): nodes may carry a
  classification, set at append; `traverse` filters by the reading actor's clearance, and
  clearance policies are versioned artifacts. Provenance-total does not mean
  visibility-total. The full model must be specified before V2.0 ships.
- **No privileged writer** (Law 9): Forge's own programs hold no capability a human actor
  cannot hold, and their appends are distinguishable only by their actor identity — which
  accumulates a public track record like anyone else's.

---

## 7. Names

The only mutable entity in the model (ADR-V2-0006).

- A **name** is a path (`prod`, `agents/support/current`, `suites/nightly`) pointing to
  exactly one revision or closure.
- **Repointing is an append** (`name-repointed`), carrying actor, times, old target, new
  target. Therefore: `resolve(name, at: position)` is total and deterministic for all time.
- **Repointing is compare-and-swap:** every repointing states the target it expects to
  replace, and the substrate rejects a repointing whose expectation is stale — Git's
  non-fast-forward rule, made law. A deploy can therefore never silently clobber a
  concurrent deploy; the rejection is itself an appended fact.
- **Resolution rule for executables:** nothing executable may embed an unresolved name;
  authoring against "latest" is resolving a name *at authoring time* into a pinned hash.
  Names are for humans and deployment; hashes are for meaning.
- **Branching** is creating a name; **merging** is appending a revision with two derivation
  parents and repointing a name at it. There is no other branch/merge machinery.

---

## 8. Universal operations

The six kernel operations (ADR-V2-0007), with normative signatures. `Address` abbreviates
§1.3; `Position` is a log position.

### 8.1 The kernel

| Operation | Signature | Semantics |
|---|---|---|
| **append** | `append(payload, provenance) → Address` | the only write (§2); rejects law-violating payloads as unrepresentable |
| **resolve** | `resolve(Address ∨ Name, at: Position = now) → Revision` | deterministic name/address resolution at any point in history |
| **traverse** | `traverse(pattern, at: Position = now) → Subgraph` | the read; patterns range over kinds, subtypes, families, verbs, times, actors |
| **diff** | `diff(a: Revision, b: Revision) → Delta` | structural delta; defined iff a and b are same-kind; over closures = architecture diff |
| **reproduce** | `reproduce(Address, under: Closure = its own) → Address` | re-execute (artifact), re-derive (claim), reconstruct-context/re-decide (decision); **undefined for observations** |
| **subscribe** | `subscribe(pattern, program: Artifact) → Address` | standing traversal bound to a program (§5) |

### 8.2 The derived vocabulary (normative reductions)

version = append(revision + derivation edge) · compare = diff · explain = traverse(evidence,
causality; backward) · replay/what-if = reproduce(·, under: modified closure) · inspect =
traverse(neighborhood) · reference/share = the address itself · comment =
append(annotation) · branch = append(name) · merge = diff + append(two-parent revision) +
repoint · validate = traverse(law patterns) ∨ reproduce · archive = append(supersession) +
repoint names away · rollback = repoint name at prior closure · deploy = decision +
repoint · blast-radius = traverse(composition, inward) · why = traverse(evidence ∪
causality, backward) · seen-before = traverse(similarity over typed history).

Nothing in this vocabulary may be implemented other than as its reduction.

### 8.3 Closure (the distinguished traversal)

`closure(artifact revision)` = the fixpoint of composition edges from that revision, all
pinned by hash. Its hash is the system's identity certificate. `diff(closure, closure)` is
the Architecture Diff; `closure(agent)` is Agent DNA; a deploy name resolves to a closure;
every observation of an executing system records the closure hash it ran under.

**What the certificate certifies (amended per adversarial review, finding B2):** a closure
hash certifies **configuration identity**, never behavioral identity. Every component
subtype declares a **pinnability class** — *pinned* (bit-identical content under the
organization's authority: prompt text, parameters, datasets), *attested* (identity by a
third party's declaration: a provider model identifier, whose underlying weights may change
without notice), or *unpinnable* (the external world: live corpora, user input) — and the
closure records which guarantee each component carries. `reproduce` accordingly returns
*new observations under the same configuration*; agreement with the original is measured
and lands as a claim. Corollary: identical closure + statistically divergent observations =
**behavioral drift**, a detectable, attributable, first-class phenomenon.

---

## 9. Lifecycles and state transitions

**Status is a query, not a column** (Law 10): no lifecycle state is ever stored; each is
*defined* as a predicate over the graph and therefore can never disagree with history. The
canonical state machines, with their defining queries:

### 9.1 Artifact continuant

```
DRAFT ──► ACTIVE ──► SUPERSEDED ──► RETIRED
            │  ▲                       (terminal, still addressable forever)
            └──┘ (new revisions)
```

- **DRAFT** — has revisions; no name resolves to it; no adopt decision exists.
- **ACTIVE** — some live name resolves to one of its revisions.
- **SUPERSEDED** — a newer revision/continuant supersedes it via derivation edge, and names
  have moved on.
- **RETIRED** — a retire decision exists and no live name resolves to it. Nothing is ever
  deleted; retirement is the *absence of names* plus the *presence of a decision*.

### 9.2 Observation

```
RECORDED ──► (annotated | disputed | cited)      — no transitions of substance; reality is static
```

An observation's "lifecycle" is the accretion of annotations, citations, and disputes around
it. It never changes state itself.

### 9.3 Claim

```
CURRENT ──► SUPERSEDED            (better reasoning / new evidence arrived)
   │
   ├──► CONFIRMED                 (calibration-outcome observations bear it out)
   └──► REFUTED                   (calibration-outcome observations contradict it)
```

CONFIRMED/REFUTED are themselves derived from evidence edges appended later — a claim's fate
is decided by observations, exactly as beliefs should be.

### 9.4 Decision

```
PROPOSED ──► ACCEPTED ──► (its effect later SUPERSEDED by a following decision)
    │
    └──────► REJECTED
```

All states derived: PROPOSED = no binding decision references it; ACCEPTED/REJECTED = a
binding decision does; effect-superseded = a later decision's intent edges point at it.

### 9.5 Name

```
CREATED ──► (repointed)* ──► RELEASED
```

A name's full history is its repointing log; RELEASED (no longer resolving) is itself a
final logged repointing.

### 9.6 The organizational lifecycle

The engineering loop (Manifesto §2.3) is the composition of the four machines: artifact
revisions beget observations (under closures), observations beget claims, claims beget
decisions, decisions beget revisions or repointings. Every V2 lifecycle stage — design,
build, version, simulate, evaluate, replay, debug, explain, optimize, verify, deploy,
observe, learn, improve — is a position on this loop, and every module claims an arc of it,
never a silo beside it.

---

## 10. System invariants

The ten laws, stated normatively (full rationale in the Manifesto, Article V):

1. **Append-only.** No update, no delete, anywhere, ever. Correction = supersession;
   withdrawal = retraction; both permanent. Regulated content may be cryptographically
   destroyed under §2's erasure rule — facts never, content when the law demands, always
   tombstoned.
2. **Total provenance.** Every append carries a signed actor.
3. **Content addressing.** Revision identity = content hash (Merkle); names are the only
   mutable state and their changes are logged appends.
4. **Epistemic partition.** Every node is exactly one of the four kinds, under that kind's
   law of revision.
5. **The Claim law.** No claim without evidence edges, named versioned method, and
   calibrated confidence; every derived number is a claim.
6. **The Decision law.** Every decision cites claims or self-declares as a judgment call.
7. **Reproducibility where reality permits.** Executables pin closures; observations record
   the closure they occurred under; observations are never "reproduced," only re-measured.
   A closure certifies configuration identity per §8.3's pinnability classes — never
   behavioral identity.
8. **Bitemporality.** Valid time and record time on every append.
9. **No privileged writer.** All actors — including Forge's own programs — under all laws.
10. **One fact, one node.** Lenses and views reference, never copy; status is a query, not
    a column.

Enforcement level: laws 1–10 are properties of the append operation itself. A violating
append does not fail validation — it has no representation.

---

## 11. Versioning

- **Unit of versioning:** the revision (§1.2). Every kind that revises (Artifact, Claim)
  revises identically: a new node + a derivation edge. There is exactly one versioning
  behavior in the entire platform.
- **Lineage** is the derivation subgraph of a continuant: linear chains, branches (multiple
  children), merges (two-parent revisions). AI Git is this subgraph plus names — commit =
  revision, branch = name, merge = two-parent revision, tag = never-repointed name,
  diff/log/blame = traversals. No git-like machinery exists; the graph *is* the repository.
- **Cross-continuant versioning** — "the system as a whole changed" — is the succession of
  closures a deploy name has resolved to: `resolve(prod, at: p)` for ranging p is the
  complete, gapless deployment history of the organization.
- **Subtype and law evolution:** subtype registrations and lens definitions are artifacts
  and version like everything else. Even this document's successors are decisions in the
  graph (Manifesto, Article X): the model versions itself under its own rules.

---

## 12. Placement rule (information architecture)

For any new piece of information, its home is decided by four questions, in order — was it
**designed** (→ Artifact), did it **happen** (→ Observation), is it **believed**
(→ Claim), was it **chosen** (→ Decision)? There is no fifth home. Anything that appears to
need one is either a subtype (register it), a lens (render it), or a composition of appends
(reduce it). If it is genuinely none of those, it is an amendment — and the bar is in
Article X.
