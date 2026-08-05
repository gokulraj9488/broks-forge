# Glossary

Every term Broks Forge uses, defined once. These names are used consistently across the product,
this documentation and the API — the same word always means the same thing.

---

**Agent** — an AI system registered for evaluation, identified by its HTTP endpoint. Framework-agnostic.

**AI Engineering Operating System** — the category Broks Forge belongs to: a system that models the
engineering act behind AI systems and reasons over it. See
[the category page](/docs/ai-engineering-operating-system).

**AI Git** — version control for engineering reasoning: revisions, promotions, rollbacks and the
rationale behind each change. Not source control. See [AI Git](/docs/ai-git).

**Artifact** — anything the platform reasons about: agent, prompt, dataset, provider, model or
evaluation.

**Benchmark** — a comparison of variants over the same dataset.

**Blast radius** — everything downstream that a change to an artifact would affect. See
[Evolution](/docs/evolution).

**Brief** — a standing, derived reading of the record. Eight exist: Daily, Deployment, Incident,
Prompt, Evaluation, Dataset, Knowledge, Architecture. Each follows *what happened → why → evidence →
impact → recommendation → next action*.

**Brok** — the Engineering Partner. Answers engineering questions from the record, deterministically.
See [Brok](/docs/brok).

**Claim** — an assertion about an artifact, supported by evidence. A derived object.

**Confidence** — a three-step verbal ladder: *consistent with*, *likely*, *near-certain*. Never a
percentage.

**Contradiction** — a claim that sits uneasily beside the evidence, for example a settled canonical
revision alongside failing evaluations of the same artifact. Reported as an inference.

**Decision** — an engineering choice with a reason: promoting a revision, deprecating an artifact. A
derived object, carrying the rationale recorded at the time.

**Derived** — computed on read from stored records; never persisted. Also an **epistemic status**
meaning "read directly from real records".

**Engineering Intelligence** — the reasoning layer: observations, claims, decisions, evidence and
knowledge derived from real work. See [Engineering Intelligence](/docs/engineering-intelligence).

**Engineering Memory** — the answers to "why is this the way it is?", derived from decisions and
recalled verbatim. See [Engineering Memory](/docs/engineering-memory).

**Engineering record** — the whole body of stored artifacts plus everything derived from them. What
Brok and the Root Cause Explorer reason over.

**Epistemic status** — how a statement is known: `derived`, `inferred`, `suggested` or `unknown`.
Exactly one per statement.

**Evaluation** — a reproducible measurement of an artifact against a dataset, with its configuration
pinned at creation. See [Evaluations & Metrics](/docs/evaluations).

**Evidence** — an evaluation framed as support for a claim or decision. A derived object.

**Evolution** — an artifact's lineage, dependents and blast radius. See [Evolution](/docs/evolution).

**Execution Graph** — the runtime path of one evaluation run, reconstructed from its own telemetry.
See [Execution Graph](/docs/execution-graph).

**Failure Graph** — the Execution Graph entered already narrowed to the broken links. The same
surface, not a second one.

**Forge Graph** — the living map of the whole system: artifacts, their real relationships, and
optionally the reasoning layered on top. See [Forge Graph](/docs/forge-graph).

**Forge Kernel** — the invisible foundation: identity, tenancy, persistence, execution. Layer 1.

**Four pillars** — the navigation model: **Build**, **Evaluate**, **Understand**, **Evolve**. Every
screen belongs to exactly one.

**Ground** — the artifacts an evaluation ran against: its agent, prompt and dataset. A precedent must
share ground with the failure in question.

**Inferred** — an epistemic status meaning a causal reading of the evidence that could be wrong.

**Investigation** — an assembled analysis of a failure: chronology, four causal layers, evidence,
precedents, story. Produced by the [Root Cause Explorer](/docs/root-cause-explorer).

**Knowledge** — a durable engineering fact that emerged from a decision plus evidence. Never
authored. See [Knowledge](/docs/knowledge).

**Observation** — a measured fact: the outcome of an evaluation against an artifact. A derived object.

**Precedent** — an earlier failure sharing an agent, prompt or dataset with the current one. What
makes "has this happened before?" answerable.

**Promotion** — activating a revision so it becomes the canonical one. A distinct act from creating
it, which is why it derives a Decision.

**Prompt** — versioned instruction text. Version notes become Engineering Memory.

**Provider** — a model provider and its credentials, encrypted at rest.

**Registry** — one catalog of every artifact and every derived knowledge object. Layer 2. See
[Registry](/docs/registry).

**Revision** — one immutable version of an artifact, with its snapshot, rationale, active state and
rollback readiness.

**Rollback** — production running an older revision than the newest. Displayed explicitly, never
implied.

**Root Cause Explorer** — the Engineering Investigation Workspace. See
[Root Cause Explorer](/docs/root-cause-explorer).

**Run** — one dataset item executed within an evaluation, with its output, latency, tokens, cost,
HTTP status and metric results.

**Substrate** — the structural identity of an artifact type: its icon and its cool-hued colour.
Never mixed with the verdict palette.

**Suggested** — an epistemic status marking a recommendation rather than a finding.

**Unknown** — both a verdict state (*not measured* — deliberately distinct from healthy) and an
epistemic status (*the record cannot answer this*).

**Unsupported decision** — a promotion with no evaluation behind it. A position carried on faith.

**Verdict** — the evaluative state of anything: `healthy`, `attention`, `risk`, `failed`, `unknown`.
The product's only evaluative vocabulary.

---

See also: [Core Concepts](/docs/core-concepts) · [FAQ](/docs/faq)
