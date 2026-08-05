# Engineering Memory

Engineering Memory is the platform's answer to *"why is this the way it is?"* — derived from the real
decisions behind an artifact, and recalled **verbatim**.

Version history tells you what changed. Memory tells you why, in the words of the person who changed
it, long after they have stopped being available to ask.

## Why it matters

Consider a prompt on version 8. Source control gives you eight diffs. What it cannot give you is the
sentence that actually matters:

> *"Softer tone after complaints."*

That sentence is the engineering content of the change. The diff shows that the word "politely" was
added; the memory explains that customers complained, and that the wording is deliberate rather than
incidental. Without it, the next engineer removes the word to shorten the prompt and quietly
reintroduces the original problem.

This is the most common failure mode in AI engineering teams: **decisions get reversed by people who
did not know they were decisions.**

## How it is created

You do not create it. You record a reason when you create a version — the notes field — and Broks
Forge does the rest.

```
   You create a prompt version and write notes
                    │
                    ▼
   You activate it (a real engineering act)
                    │
                    ▼
   DECISION derived, carrying those notes as its rationale
                    │
                    ▼
   ENGINEERING MEMORY entry:
      question:  "Why was Support Prompt changed?"
      answer:    "Softer tone after complaints."
      at:        the moment of the decision
```

The single highest-value habit in Broks Forge is writing one honest sentence in the notes field when
you create a version. Everything downstream compounds from it.

## Recalled verbatim, never re-worded

Memory is quoted, not paraphrased. When Brok answers *"What was the reasoning?"*, it returns the
recorded sentence exactly as written.

This is deliberate. A paraphrase is a new claim: it introduces an interpretation the original author
never made, and it does so invisibly. The value of memory is that it is testimony — the moment it is
rewritten, it stops being evidence of what someone actually thought.

## Where it appears

Memory travels with anything that reasons about an artifact:

- **The artifact's Intelligence tab** — the full "why" narrative for that artifact.
- **[Brok](/docs/brok)** — answers about promotions, rollbacks and revisions carry the relevant
  memory alongside them, and *"What was the reasoning?"* is a first-class question.
- **[Root Cause Explorer](/docs/root-cause-explorer)** — an investigation carries the memory of every
  artifact the failing evaluation ran against, so a precedent arrives with the reasoning behind
  whatever was done last time.
- **[AI Git](/docs/ai-git)** — each revision shows its recorded rationale on the timeline.

Every surface reads it from the same derivation, so no two can disagree.

## What it is not

- **Not a comment system.** There is no free-text field to fill in later. Memory is a by-product of a
  decision, which is what keeps it honest and current.
- **Not a changelog.** A changelog lists what shipped. Memory explains why a specific engineering
  position was taken.
- **Not generated.** No model summarises your history into a plausible-sounding rationale. If nobody
  recorded a reason, the platform says *"no reason was recorded"* — which is a true and useful
  statement, and much better than an invented one.

## The compounding effect

Memory is the mechanism by which a team stops re-litigating settled questions.

Six months in, a system with recorded memory can answer: why the temperature is 0.2, why that
dataset excludes a category, why the retry limit is 3, why the model was swapped and swapped back.
A system without it can answer none of those, and will slowly re-derive each answer at the cost of
an incident.

See also: [Engineering Intelligence](/docs/engineering-intelligence) ·
[Knowledge](/docs/knowledge) · [AI Git](/docs/ai-git)
