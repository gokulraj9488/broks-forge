# Best Practices

The quality of what Broks Forge can tell you is exactly the quality of the record you give it. These
are the habits that pay off, ordered by return on effort.

## The four that matter most

### 1 · Write a reason on every version

**The highest-leverage habit in the platform.** One honest sentence in the notes field when you
create a version.

> ✅ *"Softer tone after complaints from enterprise customers."*
> ✅ *"Dropped the few-shot examples — they were costing 400 tokens for no measurable gain."*
> ❌ *"Update"* · ❌ *"fix"* · ❌ *(blank)*

That sentence becomes [Engineering Memory](/docs/engineering-memory), recalled verbatim forever. It
is the difference between a system that can explain itself in a year and one that cannot.

Write it as if for the engineer who will undo your change without knowing why it was made — because
that is precisely who reads it.

### 2 · Evaluate before you promote

A promotion without evidence is a position the organization carries on faith. It cannot be defended
in review and cannot be safely reversed, because nobody knows what it was worth.

Ask Brok *"Should I promote it?"* first. If the answer is *"nothing has measured this revision"*,
that is the platform doing its job.

### 3 · Investigate failures; don't just restart them

Re-running a failed job is the most expensive habit in AI engineering, because it destroys the
information the failure carried. Open the [investigation](/docs/root-cause-explorer) first — even a
thirty-second read tells you whether this is new or a recurrence.

### 4 · Ask "has this happened before?" early

Precedent turns a diagnosis into a lookup. If the same ground failed nineteen days ago and the team
recorded what they did, that is the most valuable fact available — and it takes one question.

## Artifacts

**Register everything you evaluate.** An unregistered agent contributes nothing to the record and
cannot appear in the graph or in precedent.

**Name artifacts for what they do**, not for their version or owner. `Refund Support Agent`, not
`agent-v2-gk`. Names appear in answers, investigations and briefs — a good name makes those readable.

**Pin the prompt on your evaluations.** An evaluation with no prompt pinned still measures the
agent, but the investigation loses the AI Git chain and the promotion memory. Pinning it is the
difference between a two-layer causal chain and a four-layer one.

**Version datasets rather than editing them.** Importing again creates a new version, which keeps
old evaluations meaningful. Editing in place silently invalidates every result that referenced it.

## Evaluations

**Measure quality and price together.** The platform always reports latency and cost alongside
quality for a reason: a prompt that is three points better and four times more expensive is not
straightforwardly better.

**Keep datasets small enough to run often.** An evaluation you run weekly produces far more
engineering value than a comprehensive one you run twice a year, because evidence has a shelf life.

**Include the failure cases you actually care about.** A dataset of easy inputs produces a
comfortable number and no information.

**Re-evaluate after promoting.** Evidence that covers the *previous* revision is not evidence for
this one, and the platform will correctly refuse to treat it as such.

## Reading the platform honestly

**Treat `unknown` as a finding, not a gap.** It means "not measured", and it is deliberately distinct
from healthy. An estate full of `unknown` is telling you something true.

**Respect the epistemic labels.** `derived` is a fact from the record. `inferred` is a causal reading
that could be wrong. When an investigation says a related change is `inferred`, it means exactly
that — check it, do not act on it as proven.

**Chase contradictions.** A claim sitting beside failing evidence means the record is telling you two
different things. Either the failure is infrastructure, or the claim is no longer true. Both answers
are useful; leaving it unresolved is not.

**Review unsupported decisions monthly.** Ask *"What engineering decisions remain unsupported?"*.
Most teams have more than they expect, and closing them is usually the fastest available improvement
to engineering confidence.

## Team habits

**Start the day with a brief.** The Daily Brief reads the record and orders it by what needs a human
first. It takes a minute and replaces a status meeting.

**Link investigations in incident channels.** An investigation URL is a permanent, complete record —
far better than pasting a stack trace.

**Use precedent in review.** *"Has this happened before?"* is a fair question to ask of any proposed
fix, and now it has a real answer.

**Onboard through the Forge Graph.** A new engineer understands the estate faster from the graph
with reasoning enabled than from any document — including this one.

## Anti-patterns

| Don't | Because |
| --- | --- |
| Leave version notes blank | You are permanently deleting the reasoning |
| Promote without evaluating | You create a position you cannot defend or reverse |
| Edit datasets in place | You silently invalidate every past result |
| Re-run failures without reading them | You destroy the information the failure carried |
| Treat `unknown` as passing | Absence of failure is not evidence of health |
| Act on `inferred` as if `derived` | The label is there because the reading could be wrong |

See also: [The Engineering Workflow](/docs/engineering-workflow) · [Examples](/docs/examples)
