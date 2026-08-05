# Examples

Four scenarios, each showing the same failure of a conventional toolchain and what Broks Forge does
instead.

---

## 1 · The overnight failure

**Monday, 09:04.** An evaluation failed overnight. Nobody knows why.

**The usual path.** Open the job. Read the error. Open the runs list. Scroll for a failed one. Copy
the error string into a log search. Guess at the retention window. Ask in Slack whether anyone
changed anything. Wait.

**With Broks Forge.** Open the evaluation, click **Investigate**. The
[Root Cause Explorer](/docs/root-cause-explorer) has already assembled:

- **Immediate cause** — *"The agent's credentials are being rejected."* 6 of 8 sampled failed runs
  returned HTTP 401. `derived`, near-certain.
- **Contributing** — every failing run reached the same provider. `inferred`, likely.
- **Historical** — *"This has happened before — Checkout Quality #1 failed 19 days ago."* The
  recorded cause was identical, which escalates the verdict from `attention` to `risk`.
- **Related change** — *"Payments Provider credentials were rotated 3 hours before this ran."*
  `inferred`. Proximity, not proof — but it is the first thing to check.
- **Timeline** — the rotation, the run, the break, all on one axis.

The answer to *"what do I do"* was available before the first log search would have finished.

---

## 2 · The prompt that quietly regressed

**A support prompt is on v8.** Customers say the tone got worse. Nobody knows which version did it.

**The usual path.** Diff eight versions in Git. Read eight text diffs with no explanation attached
to any of them. Form a theory. Hope.

**With Broks Forge.** Open the prompt's Evolution tab and read the
[AI Git](/docs/ai-git) timeline. Each revision carries its recorded rationale:

```
   ● v8  Current production   promoted 6 days ago
   │     "Trimmed the preamble for latency."
   ● v7  Superseded
   │     "Added explicit refusal instruction."
   ● v6  Superseded
   │     "Softer tone after complaints."      ◄── the tone work
```

v6 added the tone work; v8 trimmed the preamble "for latency" — and took the tone instruction with
it. Someone removed a decision without knowing it was one.

Ask Brok *"What changed between these revisions?"* for the field-by-field diff, then
*"Show me the evidence"* to see whether any evaluation actually covered v8. Often the answer is no —
which is itself the finding.

This is the failure mode [Engineering Memory](/docs/engineering-memory) exists to prevent.

---

## 3 · The cost rise nobody can explain

**Spend is up 40% this month.** The finance question arrives on a Thursday.

**The usual path.** A cost dashboard shows the rise. It cannot say what changed, because it does not
model changes.

**With Broks Forge.** Ask Brok *"Why did cost increase?"*. It reads the real evaluation telemetry
across the period and reports the trend with its per-run breakdown — and, critically, states the
epistemic footing. If the record cannot attribute the rise, it says `unknown` rather than inventing
a cause.

Then follow the thread: *"What changed overnight?"* for the period summary, and the artifact's
Evolution tab for what was promoted in that window. If a model was swapped in a revision, the
rationale is right there on the timeline.

The platform will not pretend to a causal claim it cannot support. What it does instead is put the
spend, the changes and the reasons on the same page so a human can make the call.

---

## 4 · The promotion nobody can defend

**Design review.** Someone asks: *"why is the agent configured this way, and what proves it works?"*

**The usual path.** Silence, then archaeology.

**With Broks Forge.** Ask *"What engineering decisions remain unsupported?"*

> *"2 decisions have no evidence behind them."*
> *"'Refund Prompt v3 promoted' has no evaluation standing behind it."*

An unsupported promotion cannot be defended in review and cannot be safely reversed, because nobody
knows what it was worth. Each one comes with a next action — open the decision, or measure the
artifact.

Run the evaluation, and the decision acquires evidence. The claim becomes
[Knowledge](/docs/knowledge). Next review, the answer is a link.

---

## The pattern across all four

| | Conventional toolchain | Broks Forge |
| --- | --- | --- |
| Where the answer lives | Across 4–5 surfaces plus human memory | Assembled in one |
| What it can prove | What happened | Why, what changed, and what it means |
| Precedent | Manual recall, if anyone remembers | A first-class query |
| Rationale | Lost with the person | Recorded, verbatim, permanent |
| Result | A theory | An investigation with an audit trail |

See also: [The Engineering Workflow](/docs/engineering-workflow) ·
[Best Practices](/docs/best-practices)
