# 8. Prompt templating and versioning

- Status: Accepted
- Date: 2026-07-01

## Context

Prompts are first-class engineering assets, not incidental strings. The platform needs to
**reuse** prompts across agents and jobs, parameterise them with **variables**, **compare**
revisions, and **safely roll back** a bad change. ./0002-agent-as-central-entity.md already
anticipates this: `AgentVersion.promptVersion` is a deliberate forward reference to a Prompt
Management module. We must define that module so prompts are reproducible (an evaluation must know
exactly which prompt text ran) and so prompt changes are auditable and reversible — mirroring the
immutability stance taken for datasets in ./0007-immutable-versioned-datasets.md.

A second, security-shaped concern: a prompt template carries user-supplied variables. If templates
were ever *executed* rather than *rendered*, they would become a code-injection vector.

## Alternatives considered

- **Prompts as plain strings on agents.** A single text field per agent. No reuse, no history, no
  variables, no rollback — and no way to attribute a result to the exact prompt that produced it.
- **A templating engine that executes code.** Use a full engine with logic/expressions in
  templates. Powerful, but turns prompt text into executable code and opens an injection surface;
  far more capability than variable substitution needs.
- **Store only the latest prompt.** Keep one current version and overwrite on edit. Loses history,
  rollback and reproducibility — the same failure mode rejected for datasets.

## Decision

Model prompts as a **library entry with immutable versions**, parallel to the dataset model:

1. **`Prompt`** — the **library entry**: a named, reusable prompt with identity and metadata.
2. **`PromptVersion`** — an **immutable** revision holding the `template` text, the **extracted
   `{{variable}}` names**, author `notes`, an `active` flag and a `version_number`. Once created,
   its content never changes.
3. **Activate / rollback** toggles which version is `active`; rolling back is simply activating an
   earlier version, never editing one. This makes recovery from a bad prompt instantaneous and
   non-destructive.
4. **Comparison** diffs two versions to make a change reviewable.
5. **Templates are pure data.** Rendering is **variable substitution only** — `{{variable}}`
   placeholders are replaced with provided values and the template is **never evaluated as code**.
   This is the deliberate **injection-safe** choice.

The `AgentVersion.promptVersion` forward reference from ./0002-agent-as-central-entity.md is
reconciled with this module: it resolves to a concrete `PromptVersion` id.

## Consequences

**Positive**
- **Reuse**: one prompt serves many agents and jobs.
- **Reproducibility**: a job records the exact `PromptVersion` that ran, so results are explainable.
- **Auditability and safe rollback**: history is immutable and reverting is a one-click activation.
- **Safety**: substitution-only rendering removes the template-as-code injection surface.

**Negative / trade-offs**
- More entities (`Prompt` + `PromptVersion`) and an explicit **rendering step** versus a single
  string field.
- Substitution-only rendering is intentionally less expressive than a logic-capable engine; this
  is an accepted cost of injection safety.
- Like datasets, "editing" creates a new version rather than mutating in place — a model users must
  learn.

## Future impact

- **Prompt-vs-prompt benchmarking**: compare two prompt versions against the same agent and dataset.
- **Prompt regression detection**: flag when a new active version degrades results.
- **Prompt analytics**: track usage, performance and cost per prompt version over time.
