# Knowledge Object Catalog

Every first-class knowledge object, fully specified. Read with the [Ontology](ONTOLOGY.md) and
[Relationship Catalog](RELATIONSHIP-CATALOG.md).

## Common inheritance (stated once, not repeated)

Because every object is a kernel node, five of the twelve required fields are **inherited from the
kernel** and identical for all objects unless a deviation is noted:

- **Identity:** an opaque kernel `NodeId` (the continuant) + a content-addressed `RevisionHash` per
  state. "Which thing?" vs "which state?" are never conflated (DOMAIN_MODEL §1).
- **Versioning model:** immutable revisions; a new state is a new revision (`AddRevision`), pinned by
  hash; equal content deduplicates (Law 3). Artifacts and Claims are revisable; **Observations and
  Decisions are single-revision** (CI-6, Law 4).
- **Ownership:** the kernel `OrgId` (tenant boundary) + the `ActorId` that signed each append (Law 2).
  No anonymous writes; no privileged writer (Law 9).
- **Traceability:** total, free — every state carries its actor, valid time, and record time (Law 2,
  Law 8) and its log position; "what did we know on Tuesday?" is `resolveAt` (§Law 8).
- **Lifecycle (baseline):** created by append; superseded/annotated/retracted, never edited or deleted
  (Law 1); **status is a query, not a column** (Law 10) — e.g. "deployed", "deprecated", "refuted" are
  derived from history.

Per object below: **Purpose · Kind/subtype · Lifecycle notes · Relationships · Invariants · Evidence ·
Decision links · Security · Extension** (the five inherited fields appear only when an object deviates).

---

## A. Artifacts (intent / design) — revisable

### Prompt — `artifact/prompt`
- **Purpose:** a designed instruction template, the atomic unit of intent.
- **Relationships:** `derived_from` prior Prompt; used-by Agents (inbound `uses`).
- **Invariants:** payload carries `text` (template); MAY declare `variables`.
- **Evidence / Decision:** none required; adoption of a Prompt version is a Deployment/promotion Decision.
- **Security:** prompts may embed sensitive instructions; classified for read-visibility; regulated
  content is crypto-erasable (Law 1 amended).
- **Extension:** subtype roles (`system`, `user`, `tool`); free payload keys.

### Model — `artifact/model`
- **Purpose:** a pinned reference to a specific model version (not the weights — the *declaration*).
- **Relationships:** `uses`→Provider (CI-7, composition); `fine_tuned_from`→Model (derivation);
  used-by Agents.
- **Invariants:** payload carries `provider_ref`, `model_id`, and defaults (`context_window`, params);
  MUST compose exactly one Provider.
- **Evidence:** capabilities of a Model are separate `capability` Claims (never asserted inline).
- **Security:** may reference credentials by *handle*, never value; `pinnability` class recorded (Law 7).
- **Extension:** provider-specific params are free payload keys.

### Agent — `artifact/agent`
- **Purpose:** an executable composition — the "DNA" of a runnable AI unit.
- **Relationships:** `uses`→**exactly one** Model, `uses`→**≥1** Prompt, `uses`→Tool* / KnowledgeBase*
  (composition); `derived_from` prior Agent.
- **Invariants:** CI-4 (one Model, ≥1 Prompt). Its composition **closure** is its reproducibility
  certificate (kernel `closure`).
- **Evidence / Decision:** an Agent revision is promoted to an Environment by a Deployment Decision.
- **Security:** inherits the union of its parts' classifications.
- **Extension:** new component families via new composition verbs.

### Tool — `artifact/tool`
- **Purpose:** a callable capability an Agent may invoke (function schema, side-effect class).
- **Relationships:** used-by Agents/Workflows (`uses`); MAY `depends_on` a Provider/Environment.
- **Invariants:** payload carries `name`, `input_schema`, `side_effect` (pure|read|write|external).
- **Security:** `side_effect` drives guardrail policy; write/external tools require Policy binding.
- **Extension:** schema dialects are free payload keys.

### Workflow — `artifact/workflow`
- **Purpose:** an orchestration of Agents/Tools/sub-Workflows into a multi-step process.
- **Relationships:** `contains`→(Agent|Tool|Workflow)+ (composition, ordered); `derived_from`.
- **Invariants:** CI-5 (≥1 step); step order is significant (kernel ref order is significant).
- **Decision:** deployed like an Agent.
- **Extension:** control-flow encoded in payload (graph/DAG); new step kinds are composition targets.

### Dataset — `artifact/dataset`
- **Purpose:** a pinned, versioned collection of data used as input to evaluation/experiment/retrieval.
- **Lifecycle:** immutable once pinned; a new version is a new revision with `derived_from`.
- **Relationships:** indexed-by KnowledgeBase (`indexes`); referenced-by Evaluation/Experiment.
- **Invariants:** payload carries `content_hash`, `row_count`/`size`, `schema`, `role`
  (`evaluation-set`|`training-set`|`retrieval-corpus`); role is a tag, not a subtype (ADR-KN-0002).
- **Security:** data-classification + erasure obligations recorded; PII flag drives policy.
- **Extension:** storage backend referenced by handle; format free.

### Knowledge Base — `artifact/knowledge-base`
- **Purpose:** a retrieval capability = corpus + index/retrieval config (RAG).
- **Relationships:** `indexes`→**≥1** Dataset (role `retrieval-corpus`); used-by Agents (`uses`).
- **Invariants:** CI-5; payload carries `index_type`, `embedding_model_ref`, `retrieval` params.
- **Rationale:** a composite of Dataset(s) + config, not a duplicate of Dataset (ONTOLOGY §4).
- **Extension:** vector/keyword/hybrid via payload; re-index is a new revision.

### Memory Store — `artifact/memory-store`
- **Purpose:** the *configuration* of an agent/session memory (type, scope, retention).
- **Relationships:** used-by Agents (`uses`); its **entries are Observations** (`memory-entry`).
- **Invariants:** payload carries `scope` (agent|session|org), `strategy`, `retention`.
- **Rationale:** config is Artifact; contents are reality (Observation) — split by epistemic status.
- **Security:** retention/erasure policy; memory may accumulate PII.

### Evaluation — `artifact/evaluation`
- **Purpose:** a *definition* of how to measure quality (metrics + dataset + criteria + judge).
- **Relationships:** `uses`→Dataset (evaluation-set), `uses`→(judge Model|Tool); produces
  `evaluation-verdict` Claims (result, separate node).
- **Invariants:** payload carries `metrics`, `criteria`, `subject_type`; `mode` role (`offline`|`online`).
- **Decision:** verdicts feed Deployment `rests_on`.
- **Extension:** metric/judge plugins via Tool refs.

### Experiment — `artifact/experiment`
- **Purpose:** a controlled comparison of variants to test a hypothesis (A/B, ablation).
- **Relationships:** `contains`→variant Agent/Prompt revisions; `uses`→Evaluation/Dataset; produces
  `experiment-conclusion` Claim.
- **Invariants:** ≥2 variants; payload carries `hypothesis`, `metric`, `assignment`.
- **Extension:** multi-armed / sequential designs via payload.

### Benchmark — `artifact/benchmark`
- **Purpose:** a *published, frozen, comparable* Evaluation — the industry-standard test.
- **Relationships:** specializes Evaluation (`uses`→frozen Dataset, fixed metric); produces
  `benchmark-score` Claims across subjects.
- **Invariants:** Dataset and metric are pinned and immutable; `public` scope tag.
- **Rationale:** standardization + cross-system comparison is a distinct purpose (ONTOLOGY §4).

### Environment — `artifact/environment`
- **Purpose:** a declared deployment target context (region, resources, policy bindings).
- **Relationships:** targeted-by Deployments (`targets`); `enforces`→Policy*; bound to Names `env/<name>`.
- **Invariants:** payload carries `name`, `tier` (prod|staging|dev), resource/limit config.
- **Security:** prod environments carry stricter policy/guardrail bindings.

### Policy — `artifact/policy`
- **Purpose:** a declarative rule/constraint (intent) — cost caps, PII rules, allowed tools.
- **Relationships:** enforced-by Guardrails (`enforces`, inbound); bound to Environments.
- **Invariants:** payload carries `rule` (typed predicate), `scope`, `severity`.
- **Decision:** violations may trigger Incident Observations and remediation Decisions.

### Guardrail — `artifact/guardrail`
- **Purpose:** a runtime enforcement mechanism implementing Policies on inputs/outputs.
- **Relationships:** `enforces`→**≥1** Policy (composition); `uses`→(check Model|Tool); used-by Agents.
- **Invariants:** CI-5; payload carries `stage` (input|output|both), `action` (block|flag|redact).
- **Rationale:** mechanism (how) vs Policy (what) — ONTOLOGY §4.
- **Evidence:** a guardrail firing is a Run/Incident Observation.

### Provider — `artifact/provider`
- **Purpose:** an external vendor reference (OpenAI, Anthropic, self-hosted).
- **Relationships:** provides Models (`uses`, inbound from Model).
- **Invariants:** payload carries `name`, `endpoints`, `auth_handle` (never secret value).
- **Security:** credentials by handle only; SOC/DPA metadata recorded.

### Artifact Package — `artifact/artifact-package`
- **Purpose:** a named, releasable bundle pinning a set of artifacts by hash (a "release").
- **Relationships:** `includes`→**≥1** Artifact (composition); `derived_from` prior package.
- **Invariants:** CI-5; the package **is** a curated closure; its root hash certifies the release.
- **Decision:** a package is promoted by Deployment.

---

## B. Observations (reality) — single-revision

### Run — `observation/run`
- **Purpose:** the record of one execution — the core reality object.
- **Lifecycle:** single revision (CI-6); never revised.
- **Relationships:** `executed`→**exactly one** Agent/Workflow revision (derivation, CI-1);
  `contained_by` Session; `triggered`→Incident (causality); `generated`→MemoryEntry.
- **Invariants:** payload carries `inputs`, `outputs`, `status`, `latency_ms`, **`cost`** (tokens+$),
  `closure_hash` of what ran (Law 7).
- **Evidence:** Runs are the primary evidence for Evaluation/Capability/Cost Claims (`cites`, inbound).
- **Security:** inputs/outputs may contain user data; classified; erasable.

### Session — `observation/session`
- **Purpose:** a bounded sequence of Runs (a conversation/task), the continuity scope for Memory.
- **Relationships:** `contains`→Run+ (composition); `uses`→MemoryStore.
- **Invariants:** single revision; payload carries `started`, `actor`, `channel`.

### Incident — `observation/incident`
- **Purpose:** a recorded failure/degradation event.
- **Relationships:** caused-by Deployment/Run (`caused`/`triggered`, inbound); explained-by RootCause
  Claim; remediated-by Decision.
- **Invariants:** single revision; payload carries `severity`, `detected_at`, `symptoms`.
- **Decision:** links to rollback/remediation Decisions.

### Human Feedback — `observation/human-feedback`
- **Purpose:** the primary fact that a human gave feedback (rating, thumbs, correction).
- **Relationships:** `measured_by`/`cites` target Run/Session; feeds Evaluation Claims.
- **Invariants:** single revision; payload carries `rater`, `signal`, `target_ref`.
- **Rationale:** reality (a human said X), not a derived verdict (ONTOLOGY §4).

### Memory Entry — `observation/memory-entry`
- **Purpose:** one written unit of agent/session memory (reality accumulated during runs).
- **Relationships:** `generated_by`→Run (derivation); scoped to a MemoryStore.
- **Invariants:** single revision; append-only accumulation is how memory "changes".
- **Security:** retention/erasure per MemoryStore policy.

---

## C. Claims (belief) — revisable; inherit the kernel Claim law (evidence + method + confidence)

### Evaluation Verdict — `claim/evaluation-verdict`
- **Purpose:** the derived judgment of an Evaluation ("agent scores 0.82 on helpfulness").
- **Relationships:** `cites`→Run+/HumanFeedback+ (evidence); `measured_by`→Evaluation (method);
  rests-on target for Deployment.
- **Invariants:** kernel Claim law + CI-2; `method` = the Evaluation ref/id; confidence calibrated.

### Experiment Conclusion — `claim/experiment-conclusion`
- **Purpose:** the causal conclusion of an Experiment ("variant B beats A, p<0.05").
- **Relationships:** `cites`→Runs of each variant; `measured_by`→Experiment.
- **Invariants:** Claim law; must reference ≥2 variants' evidence.

### Benchmark Score — `claim/benchmark-score`
- **Purpose:** a subject's score on a Benchmark (comparable across systems).
- **Relationships:** `cites`→Runs on the frozen Dataset; `measured_by`→Benchmark.
- **Invariants:** Claim law; frozen Dataset+metric make it comparable.

### Capability — `claim/capability`
- **Purpose:** an evidenced belief about what a Model/Agent can do.
- **Relationships:** `cites`→BenchmarkScore/Run evidence; subject = Model/Agent.
- **Invariants:** Claim law; no bare capability tags (ONTOLOGY §4).

### Root Cause — `claim/root-cause`
- **Purpose:** the diagnosed cause of an Incident.
- **Relationships:** `cites`→Runs/Observations; `detected_by`→Run; explains an Incident.
- **Invariants:** Claim law; causality asserted as a justified belief (DOMAIN_MODEL §193).

### Cost Rollup — `claim/cost-rollup`
- **Purpose:** an aggregate cost over a scope ("$1,204 for agent X in July").
- **Relationships:** `cites`→Run+ (evidence); `method` = `aggregation`.
- **Invariants:** Claim law; the merged-out "Cost object" lives here (ONTOLOGY §4).

---

## D. Decisions (will) — single-revision; inherit the kernel Decision law

### Deployment — `decision/deployment`
- **Purpose:** the act of promoting an Agent/Workflow/Package revision to an Environment.
- **Mechanism:** a Deployment Decision node **plus** a kernel Name repoint (`deploy/<env>/<target>`),
  ADR-V2-0006 — will and mechanism, correctly separated.
- **Relationships:** `applied`→**exactly one** Agent/Workflow/Package revision (intent);
  `targets`→**exactly one** Environment (intent); `rests_on`→EvaluationVerdict+ (intent) *or*
  `judgment-call`.
- **Invariants:** kernel Decision law + CI-3; single revision; "currently deployed" is a query
  (`resolve` the name), not a field (Law 10).
- **Security:** prod deployments require policy-satisfying evidence or a signed judgment call.

**General decision subtypes** (same machinery, catalog-registered): `promotion`, `rollback`,
`approval`/`rejection` (the AI Pull Request — three appends, ADR-V2-0004), `retirement`,
`accept-risk`. Each is a Decision that `rests_on` claims or self-declares.

---

## E. Non-objects (explicitly rejected as first-class)

- **Cost** → merged into Run (measured) + `cost-rollup` Claim (aggregate). No independent identity.
- **"Status"** → never an object or a field; a query over history (Law 10). "Deployed", "deprecated",
  "refuted", "resolved" are all derived.
- **Actor/User** → reified provenance, not a knowledge object (MANIFESTO §2.5); every append names one.

Every mission-listed candidate is now either a first-class object (A–D) or a justified non-object (E).
