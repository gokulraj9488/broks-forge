# The Brok's Forge V2 Core Architecture Manifesto

**The constitution of the operating system.**

**Status:** Ratified as the conceptual foundation of V2. Contains no implementation details,
no technology choices, and no interface designs — deliberately. Every future implementation
inherits this document; implementations change, this does not.

**Companion documents:**
- [FORGE_KERNEL.md](../FORGE_KERNEL.md) — the research derivation: how these concepts were
  discovered, which candidates were rejected, and why the reduction stops where it does.
- [adr/](adr/README.md) — one Architecture Decision Record per primitive: why each exists.
- [DOMAIN_MODEL.md](DOMAIN_MODEL.md) — the canonical, normative domain model.

---

## Preamble: what is being founded

Brok's Forge V2 is not a product in the AI-tools market. It is the founding of a category:
**the operating system for AI engineering.**

The claim "operating system" is technical, not rhetorical. Every system that earned the title
provided the same five things: a universal resource abstraction, a small closed operation set,
laws enforced by the substrate rather than by convention, a reaction model that runs without a
human driving, and a userspace — the guarantee that everything visible is a program written
*on* the abstraction, never *into* it. Unix had the file. Git had the object. Docker had the
image. Kubernetes had the resource.

AI engineering has no such substrate. It has point tools: telemetry viewers, test runners,
prompt managers, experiment trackers. Each owns one region of the engineering loop and forgets
everything outside it. The work of AI engineering — what was designed, what actually happened,
what it was believed to mean, and what was chosen in response — evaporates daily, in every
team, everywhere.

This constitution defines the substrate on which that work stops evaporating.

---

## Article I — The Substrate

**§1.1** There is exactly one structure in Forge, per organization: **the Forge Graph** — an
append-only, content-addressed, bitemporal, provenance-total, typed graph of engineering
facts.

**§1.2** The Forge Graph is not a database schema, a trace store, or a knowledge graph. It is
distinguished from all of these by its **write discipline**, which is the heart of the entire
architecture:

- *Append-only.* The only write is append. Nothing is edited; nothing is deleted. Correction
  is supersession; retraction is a recorded act. History is not a feature of the system —
  history **is** the system.
- *Content-addressed.* Every immutable revision is identified by the hash of its content,
  Merkle-style. Identity, equality, deduplication, structural diff, and reproducibility
  certificates all follow from this one property.
- *Bitemporal.* Every fact carries when it was true and when it was recorded, so "what did we
  know on Tuesday?" is always answerable.
- *Provenance-total.* Every append is signed by an actor — human or machine. There are no
  anonymous facts and no privileged writers.

**§1.3** The graph's append log **is** its event stream. There is no second system of record
for "what happened"; the history and the record are the same structure viewed by time and by
shape. (This subsumes what earlier drafts called the Evidence Ledger.)

**§1.4** **Names are the only mutable state.** A name (`prod`, `support-agent`) is a pointer
to a revision; repointing it is itself a logged append. Deployment, rollback, promotion, and
branching are name operations — atomic, instant, reversible, and fully historied.

**§1.5** A snapshot of an entire AI system is not a stored object but a **closure**: the
transitive pin of a node's composition references. Reproducible deployment, architecture
diff, agent identity, and replay are all views over closures.

**§1.6** **The append is the act of publication.** The kernel governs the *published*
engineering record; what precedes an append — editor buffers, private workbenches,
half-formed drafts — is pre-kernel space, outside the laws' jurisdiction, exactly as Git's
object database does not govern the working tree. Publication granularity is the author's
choice. *(Added by adversarial review, finding C1.)*

---

## Article II — Epistemic Typing (the founding invention)

**§2.1** Every node in the Forge Graph is exactly one of **four kinds**, distinguished not by
domain (agent, prompt, incident) but by **epistemic status** — the relationship the fact has
to truth:

| Kind | Epistemic status | Law of revision |
|---|---|---|
| **Artifact** | Intent — what we designed | Superseded by new revisions |
| **Observation** | Reality — what happened | Never revised; only annotated or re-measured |
| **Claim** | Belief — what we think it means | Superseded by better reasoning or new evidence |
| **Decision** | Will — what we chose | Never unmade; only followed by new decisions |

**§2.2** The kinds cannot merge, because different laws of revision apply to each: an
observation cannot be superseded by argument; a claim can; a decision is neither true nor
false, only made. Any proposal to merge two kinds must defeat this test and will fail.

**§2.3** The kinds form the engineering loop, and the loop is the lifecycle of everything:

```
        design                 run / measure               interpret
  ┌──► Artifact ───────────► Observation ─────────────► Claim ──┐
  │                                                              │  decide
  └───────────────────────── Decision ◄──────────────────────────┘
         (a new revision — or the recorded choice not to change)
```

**§2.4** This discipline — **Epistemic Typing** — is Forge's contribution to the field, in
the sense that *commit* was Git's and *desired state* was Kubernetes'. It does not currently
exist in AI engineering: no existing tool distinguishes what was designed from what happened
from what is believed from what was chosen, and therefore no existing tool can be trusted
about any of them. The discipline is defined so that future tools can adopt it independently
of Forge; if they do, the category has been created.

**§2.5** Actors — engineers, teams, and Forge's own analyzers — are reified provenance, not a
fifth kind. Every append already names its actor; the actor node merely makes that identity
addressable.

---

## Article III — Relationships

**§3.1** Relationships are first-class: addressable, immutable, provenance-stamped assertions
with validity intervals — never foreign keys. An edge can be cited as evidence, claimed
about, and retracted by a later append. Every relationship therefore contains its own history.

**§3.2** Relationship verbs are an open set, but every verb belongs to one of **five closed
families**, and the family — not the verb — carries the semantics the system relies on:

| Family | Meaning | Powers |
|---|---|---|
| **Composition** | is built from | closures, agent DNA, blast radius |
| **Derivation** | came from | lineage, version history, AI Git |
| **Evidence** | is justified by | the Claim law, every "how do you know?" |
| **Causality** | brought about | root cause, failure propagation, incidents |
| **Intent** | was chosen by | decision replay, accountability, memory of judgment |

**§3.3** A sixth family would be a constitutional amendment (Article X). The claim of this
constitution is that none exists in the domain of engineering.

---

## Article IV — The Operations

**§4.1** The kernel exposes exactly **six operations**, closed under composition:

1. **append** — the only write.
2. **resolve** — name or address → revision; deterministic at any log position.
3. **traverse** — the read: inspection, search, "why?", blast radius; *closure* is its
   distinguished form.
4. **diff** — structural delta between any two nodes of the same kind.
5. **reproduce** — re-run a thing under its pinned closure; for claims, re-derive; for
   decisions, reconstruct context and optionally re-decide (the what-if).
6. **subscribe** — a standing traversal whose matches invoke programs whose outputs are
   appends.

**§4.2** Every operation engineers name — version, compare, explain, replay, branch, merge,
comment, share, validate, archive — is a composition of the six. No seventh operation may be
added to the kernel while a composition of the six expresses it.

---

## Article V — The Laws

Laws are enforced by the substrate: violating appends are unrepresentable, not flagged.

1. **Append-only.** No engineering *fact* is ever deleted or edited; only superseded,
   retracted, annotated. Regulated *content* (personal data under erasure law) may be
   cryptographically destroyed — the node's identity, hash, edges, and place in history
   remain, and the destruction is itself a permanent, attributed, authorized fact.
   *(Amended by adversarial review, finding D1.)*
2. **Total provenance.** Every append is signed by an actor.
3. **Content addressing.** Revision identity is content hash; names are the only mutable
   state, and name changes are logged.
4. **Epistemic partition.** Every node is exactly one kind, under that kind's law of
   revision. No belief may be laundered as a fact, nor a fact as a design.
5. **The Claim law.** A claim cannot exist without evidence references, a named method, and a
   calibrated confidence. Since every derived number must be a claim, **no unexplained number
   can exist anywhere in Forge.**
6. **The Decision law.** A decision cites the claims it rests on — or explicitly declares
   itself a judgment call, with a name attached. Honesty outranks ceremony.
7. **Reproducibility where reality permits.** Everything executable pins its closure; every
   observation records the closure it occurred under. A closure hash certifies
   **configuration identity**, not behavioral identity: components declare their
   pinnability (pinned / attested / unpinnable), `reproduce` yields *new observations under
   the same configuration*, and agreement with the original is measured, never assumed —
   which makes behavioral drift under an identical closure a detectable, first-class
   phenomenon. (Observations themselves are not replayable; the world is not.)
   *(Amended by adversarial review, finding B2.)*
8. **Bitemporality.** Valid time and record time on every fact.
9. **No privileged writer.** Forge's own analyzers obey every law and are scored by the same
   graph they write to.
10. **One fact, one node.** Views reference; nothing copies. Corollary: **status is a query,
    not a column** — lifecycle state is derived from history and can never disagree with it.

---

## Article VI — Reaction and Autonomy

**§6.1** All autonomy is **subscription**: a standing pattern over the log, bound to a
program, whose every output lands as law-abiding, actor-signed appends. There is no other
execution model — no privileged pipelines, no invisible magic. The substrate itself emits
one observation stream — **clock ticks** — so that scheduled behavior is a subscription
like any other and even the passage of time is in the record. *(Amended by adversarial
review, finding B3.)*

**§6.2** The learning loop closes structurally: when a claim's recommended action is taken up
by a decision and later observations confirm or refute the expected result, the confidence
calibration of the method that produced the claim is adjusted. **Forge's judges are judged by
the record they write.**

**§6.3** The platform is a participant, not an editor. It appears in its own graph as an
actor with a track record and earns authority the way engineers do: by being right, visibly,
over time.

---

## Article VII — Memory

**§7.1** Memory is **structural, not searched**. "We have seen this before" is subgraph
similarity over typed history, not full-text luck. Every incident makes the next one cheaper;
the organization's returns on Forge compound.

**§7.2** **The Trail** (the second invention): an investigation — the actual path an engineer
walks from symptom to cause — is itself appendable as a first-class object. Debugging
sessions stop evaporating; the act of understanding becomes engineering memory, shareable
mid-flight, resumable, citable as evidence.

---

## Article VIII — The Human Interface Philosophy

*(Conceptual commitments only; no interface design belongs in this constitution.)*

**§8.1** There are no pages. Pages are where systems put information when they do not know
how it relates; Forge always knows.

**§8.2** The navigation model has five concepts: **Focus** (you are always at a node, with a
stable address), **Frontier** (the ranked relevant neighborhood; navigation is traversal),
**Lens** (a projection appropriate to the focused kind; the same fact under many lenses,
never copied), **Trail** (§7.2), and **the Palette** (the shell: the six operations over
names, at thought speed).

**§8.3** Engineers move by asking, not by browsing: *why? what changed? what depends on this?
what happened next? have we seen this before?* Each question is a frontier move.

---

## Article IX — Userspace

**§9.1** Everything outside Articles I–VIII is userspace: every module, screen, report,
analyzer, and autonomous behavior is a program written against the kernel. Userspace may be
rewritten forever without touching a single stored fact.

**§9.2** Nothing in userspace may acquire kernel privileges. A feature that requires a new
kind, family, operation, or law is not a feature request; it is an amendment (Article X).

---

## Article X — Amendment

**§10.1** This constitution changes only by supersession, never by edit — it obeys its own
Law 1.

**§10.2** The bar for amendment:
- A **new kind** must demonstrate a law of revision distinct from all four existing kinds.
- A **new edge family** must demonstrate semantics no existing family carries.
- A **new operation** must be inexpressible as a composition of the six.
- A **new law** must be enforceable by the substrate, not by review.

**§10.3** Whatever fails this bar is userspace.

---

## The Category Declaration

Every category-defining platform introduced at least one concept engineers now think in.
Forge introduces three, each adoptable by future tools independently of Forge itself:

1. **Epistemic Typing** — typing engineering facts by their relationship to truth (intent /
   reality / belief / will), each type under its own law of revision. The type system for
   knowledge that AI engineering has been missing.
2. **The Claim** — the evidence-bound unit of derived knowledge: no number without method,
   evidence, and calibrated confidence. The end of naked numbers.
3. **The Trail** — the investigation as a first-class, persistent, shareable engineering
   object. The end of evaporating understanding.

## The Mental Model, in one paragraph

Git for what you built. A flight recorder for what happened. A court record for what you
believed and what you chose. One append-only structure, four kinds of fact, five kinds of
relationship, six operations, ten laws — and everything else, forever, is just a program.
