# AI Git

AI Git is **version control for engineering reasoning**. Every revision of an artifact, which one is
promoted, what it superseded, whether it can be rolled back, and the rationale behind each change.

It is not source control, and the difference is the whole point.

## Source control versus AI Git

| | Git | AI Git |
| --- | --- | --- |
| Unit | A commit of text | A revision of an engineering artifact |
| Answers | What text changed | What was promoted, and why |
| "Current" means | Whatever HEAD points at | The revision **production is actually running** |
| Rollback | Revert a commit | A recorded engineering position with its own evidence |
| Rationale | A commit message, often "fix" | Recorded rationale that becomes Engineering Memory |
| Evidence | None | Evaluations that cover this specific revision |

Your prompt text may well be in Git too. That tells you the characters changed. AI Git tells you
that v3 was promoted over v2 on a Tuesday, that four evaluations covered it, that the recorded
reason was *"Softer tone after complaints"*, and that production is currently on v2 because someone
rolled back at 2am.

## What a revision holds

- **Label** — `v3`, or a semantic version
- **Snapshot** — the full field-by-field state at that point
- **Rationale** — the reason recorded when it was created
- **Active** — whether this is the promoted revision
- **Rollback-ready** — whether the platform can return to it
- **Timestamp** — when it was created

Revisions are immutable. Promotion is a separate act from creation, which is what makes promotion a
[Decision](/docs/core-concepts) worth recording.

## The deployment timeline

The timeline reads the real revision records and shows every promotion and rollback:

```
   ● v4   Superseded          created 2 days ago
   │      "Trimmed the system preamble."
   │
   ● v3   Current production  promoted 5 days ago     ◄── active
   │      "Softer tone after complaints."
   │
   ○ v2   Superseded · rollback-ready
   ○ v1   Superseded
```

When the active revision is **not** the newest one, that is a rollback, and it is shown as one:

> **Rolled back.** Production is on v3 even though a newer revision (v4) exists — the newer revision
> was rolled past.

That state is invisible in source control and critical in engineering. A team running an older
revision on purpose is carrying an unresolved question, and the timeline says so.

## Comparing revisions

Any two revisions can be compared field by field. The diff is a real snapshot comparison, not a text
diff — so it names the field that actually changed (`template`, `temperature`, `endpointUrl`) rather
than showing you line noise.

This is the shortest path from "something changed before this failure" to "this specific field
changed". Both Brok (*"What changed between these revisions?"*) and the
[Root Cause Explorer](/docs/root-cause-explorer) route to it.

## Rationale becomes memory

The notes you write when creating a version are not decoration. They become the rationale on the
derived Decision, and from there they become
[Engineering Memory](/docs/engineering-memory) — recalled verbatim, forever.

If you write nothing, the platform reports *"No rationale was recorded for this revision"*. That is
an honest statement rather than an invented one, but it is also a small permanent hole in your
engineering record.

## Which artifacts are versioned

Prompts, agents and datasets carry revisions. Providers and evaluations do not — an evaluation is
already an immutable measurement, and a provider is a configuration rather than an engineering
position.

## Where to find it

AI Git lives inside each artifact's **Evolution** workspace as the deployment timeline. From there
you can compare revisions, read each rationale, and see rollback readiness.

Brok reaches it directly: *"Why was X promoted?"*, *"Should I promote it?"*, *"Should I roll back
X?"*, *"What changed between these revisions?"*, *"What was the reasoning?"*.

See also: [Evolution](/docs/evolution) · [Engineering Memory](/docs/engineering-memory) ·
[Core Concepts](/docs/core-concepts)
