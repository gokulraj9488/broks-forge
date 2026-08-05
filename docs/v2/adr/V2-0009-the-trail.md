# V2-0009. The Trail — investigations as first-class objects

- Status: Accepted
- Date: 2026-07-28
- Level: Conceptual (no implementation content)

## Context

The costliest recurring loss in engineering organizations is not code and not data — both
are versioned. It is **understanding**. An engineer spends six hours walking from a symptom
to a root cause: which components they suspected, what they ruled out and why, where the
trail went cold, what finally cracked it. The next morning that walk exists only in one
person's head; six months later it exists nowhere, and the next engineer with the same
symptom pays the six hours again.

Existing tools preserve the *inputs* to understanding (logs, traces, dashboards) and
sometimes the *conclusion* (a postmortem document), but never the *path* — and the path is
where the reusable knowledge lives (what to check first, what looks guilty but isn't).

The navigation model (Manifesto, Article VIII) makes the path suddenly capturable: if all
investigation is traversal of the graph — focus to focus along edges, lenses applied,
diffs taken — then an investigation *is a sequence of kernel operations*, and a sequence of
kernel operations is data.

## Alternatives considered

- **Postmortem documents.** Preserve conclusions, lose the path; unstructured, unlinked to
  the objects they discuss, discoverable only by title.
- **Session logs / analytics events.** Capture clicks, not epistemics: no distinction
  between "visited" and "ruled out," no annotations, no shareable mid-flight state.
- **Comments scattered on objects.** Preserve fragments of reasoning at nodes, but the
  connective tissue — the order, the dead ends, the pivot — is exactly what a scatter
  cannot hold.
- **Do nothing (trails as a future feature).** Rejected because first-class status changes
  the design of navigation itself (every traversal must be recordable), and retrofitting
  recordability is far harder than designing it in.

## Decision

**The Trail is a first-class object**: a recorded traversal — the ordered sequence of foci,
edges followed, lenses applied, diffs taken, dead ends marked, and annotations made — that
can be appended to the graph like any other fact.

Its standing in the kernel: a trail is an **Observation** (it records what an actor actually
did — reality, not intent or belief), composed of references to the nodes visited. It needs
no fifth kind. Its consequences, however, justify a founding ADR:

- **Shareable mid-flight**: a colleague joins your investigation by resolving its address —
  your exact epistemic position, not a screenshot of it.
- **Resumable**: tomorrow continues where today stopped, with the ruled-out branches still
  ruled out.
- **Citable as evidence**: a root-cause claim can cite the trail that found it — the Claim
  law's evidence edges pointing at the *process of discovery* itself.
- **Memory of method**: engineering memory (seen-before matching) can surface not only
  "this happened before and the fix was X" but "here is the path that found it" — the
  senior engineer's instinct, made transferable.
- **The act of investigating becomes engineering knowledge**, automatically, at zero
  authoring cost — the record is a by-product of the navigation model, not a writing task.

The Trail is declared one of Forge's category inventions (Manifesto, Category Declaration):
no existing engineering tool of any kind — AI or otherwise — treats the investigation as a
durable, addressable object.

## Consequences

**Positive**
- Debugging knowledge compounds instead of evaporating; onboarding gains a library of real
  investigations rather than sanitized runbooks.
- Zero ceremony: recording requires no effort beyond the navigation engineers already do.

**Negative / trade-offs**
- Trails observe engineer behavior, so they carry workplace-surveillance risk if misused;
  the domain model must give the trail's actor control over publication (record locally,
  publish deliberately), and published trails must serve learning, never evaluation of
  people. This is an ethical constraint stated at the constitutional level on purpose.
- Raw trails are noisy (every wrong turn included); their value depends on lenses that
  summarize a trail's shape — a userspace problem, but one the invention's usefulness
  hangs on.
