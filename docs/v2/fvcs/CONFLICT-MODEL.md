# FVCS Conflict Model & Merge Strategy

Merging versions of *engineering knowledge* is richer than merging files: two branches can be
byte-clean yet produce an AI system that is semantically wrong or operationally unsafe. FVCS therefore
classifies conflicts on **three levels** and resolves each with an explicit `Decision` — never a silent
auto-pick of ambiguous cases.

## 1. The merge base

A merge is three-way. The **base** is the lowest common ancestor (LCA) of the two branch heads in the
commit DAG (`parent` edges). Determinism: ancestors are ordered by kernel `LogPosition`; if a single LCA
exists it is the base. **Criss-cross history** (multiple LCAs) is detected and reported (a recursive
merge that synthesizes a virtual base is a documented future strategy); FVCS never guesses a base.

For each continuant in `base ∪ A ∪ B`, let `b`, `a`, `x` be its pinned revision in base/A/B (or absent):

| base | A | B | Outcome |
|---|---|---|---|
| b | b | b | unchanged → b |
| b | a≠b | b | A changed only → **take a** |
| b | b | x≠b | B changed only → **take x** |
| b | a≠b | x=a | both made the same change → **take a** (dedup) |
| b | a≠b | x≠b, x≠a | both changed differently → **STRUCTURAL CONFLICT** |
| b | absent | b | A removed, B unchanged → **remove** |
| b | a≠b | absent | A modified, B removed → **modify/remove CONFLICT** |
| absent | a | x≠a | both added same logical name, different identity → **add/add CONFLICT** |
| absent | a | a | both added identical → **take a** |

"Logical name" for add/add is the object's `role`/name payload within its type (e.g. two prompts both
named `system`), since content identity differs. Structurally clean columns auto-merge; the rest halt.

## 2. Conflict taxonomy (the three levels)

### Structural conflicts — *the same thing changed two ways*
The unit is the **continuant** (`NodeId`). Detected purely from the three-way table above — no semantics
needed. Deterministic and complete for the pinned member set.
- **Concurrent prompt edits** → structural (same Prompt, two revisions).
- **Workflow evolution** where both sides edit the same Workflow → structural.
- **Multi-agent evolution** where both sides edit the same Agent → structural.
- modify/remove and add/add are structural sub-cases.

*Resolution:* an explicit resolution `Decision` per conflicted continuant — **take-ours**, **take-theirs**,
or **author a new merged revision** (which itself `derived_from` both sides). The merge commit records
the chosen snapshot.

### Semantic conflicts — *individually clean, jointly wrong*
The merged snapshot has no same-continuant clashes, but the *combination* is invalid or beliefs
contradict. Not detectable from the member diff alone — requires re-validation of the merged snapshot
against the ontology invariants and a scan of the relevant Claims.
- **Divergent evaluation conclusions** → semantic: branch A's `EvaluationVerdict` says the agent is good,
  B's says it regressed, about overlapping subjects/methods. Both are valid Claims (they coexist in
  history), but a merge that promotes one line must resolve which belief governs — via a **superseding
  Claim** (`supersedes`) or a re-evaluation on the merged snapshot.
- **Policy conflicts** where two Policies are individually valid but contradictory when both enforced
  (e.g. "allow tool X" vs "forbid tool X") → semantic (surfaces when both are in the merged Guardrail set).
- **Workflow reordering** that changes behavior without a same-node edit → semantic.

*Resolution:* a re-evaluation (new `EvaluationVerdict` on the merged snapshot) and/or a superseding Claim
or an `accept-risk`/judgment-call `Decision`. FVCS flags the semantic risk; it does not fabricate a
verdict.

### Operational conflicts — *valid as data, unsafe to run*
The merge is structurally and semantically fine as recorded knowledge, but applying the merged Snapshot
to a target Environment violates a runtime/compatibility/policy constraint.
- **Dataset version incompatibilities** → operational: a new Dataset schema breaks a consuming
  Evaluation/KnowledgeBase contract; surfaces at use, detected by the compatibility engine.
- **Model replacement** → operational/semantic: the new Model's `Capability` claims must cover what the
  Agent requires; a `CompatibilityVerdict` decides.
- **Policy caps** (e.g. cost/latency budget of the target Environment) that the merged config would
  exceed → operational.

*Resolution:* a `CompatibilityVerdict` Claim (evidenced) plus, if proceeding despite risk, an explicit
waiver `Decision` (judgment-call). Deployment of an operationally-conflicted snapshot is blocked until
a compatibility verdict + (if needed) a waiver exist.

## 3. Which conflicts are which (the mission's list)

| Change | Level | Detected by |
|---|---|---|
| Concurrent prompt edits | Structural | three-way member diff |
| Divergent evaluation conclusions | Semantic | Claim scan on merged snapshot |
| Policy conflicts | Semantic → Operational | invariant re-validation; policy check at deploy |
| Workflow evolution | Structural (same node) / Semantic (reorder) | member diff / behavior re-eval |
| Dataset version incompatibilities | Operational | compatibility engine (schema/interface) |
| Model replacement | Operational/Semantic | capability compatibility verdict |
| Multi-agent evolution | Structural + Semantic | member diff + interaction re-eval |

## 4. Merge strategy (algorithm)

```
merge(branchA, branchB):
  headA, headB   = resolve(A), resolve(B)
  base           = LCA(headA, headB)            # commit DAG; deterministic by LogPosition
  if multiple LCAs: return CRISS_CROSS report   # recursive strategy = future
  snapA, snapB, snapBase = snapshots of the three commits
  changes        = three-way over (base ∪ A ∪ B) members     # §1 table
  structural     = conflicts from the table
  if structural and no resolutions supplied:
       return ConflictReport(structural)         # halt; caller resolves with Decisions
  merged         = apply(auto-merges + resolutions)          # a new ArtifactPackage
  semantic       = revalidate(merged) + claimContradictions(merged)   # §2
  operational    = compatibility(merged, targetEnv?)                  # §2 (if a target given)
  commit         = Commit(records=merged, parent=[headA, headB], message)
  advance(A, commit)  # CAS
  return MergeResult(commit, semantic-warnings, operational-warnings)
```

- **Auto-merge** happens only for the unambiguous rows of §1. Everything else is explicit.
- **Structural** conflicts *block* the merge commit until resolved.
- **Semantic** and **operational** findings are *warnings on the merge result* (the data merges, but
  promotion/deploy should not proceed until addressed) — because they are properties of *using* the
  merge, not of *recording* it. This separation (block vs. warn) is deliberate and matches the levels.
- The merge is a `Commit` with ≥2 parents; determinism holds because inputs are content hashes and base
  selection is position-ordered.

## 5. Properties (for the adversarial review)

- **No merge ambiguity in the auto path:** every §1 row is either deterministic or an explicit conflict;
  nothing ambiguous is silently resolved.
- **Deterministic:** same two heads ⇒ same base ⇒ same change set ⇒ same conflict set (content-addressed
  inputs, position-ordered base).
- **No history corruption:** a merge only *appends* a commit and CAS-advances a Name; both parents and
  all pre-merge commits remain permanently in history.
- **Reproducible outcome:** the merged Snapshot is an ArtifactPackage with a closure root hash — the
  merge result is itself a reproducible version.
