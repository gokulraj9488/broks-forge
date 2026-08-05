# Brok — the Engineering Partner

Brok answers engineering questions about your AI system, in plain English, from your own engineering
record.

It is not a chatbot, and it is not ChatGPT in a dashboard. There is no language model behind it. Ask
it something the record cannot answer and it will say so.

## What it does

You ask *"Why did last night's deployment fail?"* and Brok reads the evaluation record, the failed
runs, the provider attribution and the artifacts involved, then answers with:

- **A verdict** — one line saying what is true, with its epistemic status and confidence.
- **Reasoning** — a chain of statements, each declaring whether it is *derived* or *inferred*, and
  what it was read from.
- **Impact** — what this holds open, in engineering terms.
- **Evidence** — the actual records it read, clickable.
- **Recommendations** — each with its reasoning, its confidence, and a **next action into a real
  surface** of the product.
- **Follow-ups** — engineering-specific questions worth asking next.

Every answer follows that shape. It is the constitutional narrative, and it is enforced by the data
model rather than by convention.

## The conversation carries context

The thing that makes Brok feel like a partner rather than a search box: **you never restate the
subject.**

```
   You:  Why was Support Prompt promoted?
   Brok: ... (subject resolved: Support Prompt)

   You:  Show me the evidence.
   Brok: Read as a question about Support Prompt, carried from
         "Why was Support Prompt promoted?" ...

   You:  Compare it with v1.
   You:  Open the graph.
   You:  Should I promote it?
   You:  What was the reasoning?
```

Six turns, one subject named once. And notice the second answer: the inherited subject is
**declared**, not assumed silently. An inherited context is an auditable claim, so if Brok carried
the wrong subject forward, you can see it immediately.

## The questions it answers

Brok resolves **25 engineering intents**. These are genuinely different workflows, not rephrasings.

| Intent | Example question |
| --- | --- |
| `failure.explain` | Why did yesterday's deployment fail? |
| `execution.explain` | Why is this graph red? |
| `evaluation.explain` | Explain this evaluation. |
| `history.similar` | **Has this happened before?** |
| `promotion.rationale` | Why was Prompt v7 promoted? |
| `promotion.advice` | Should I promote it? |
| `evidence.show` | Show me the evidence. |
| `graph.view` | Open the graph. |
| `memory.why` | What was the reasoning? |
| `rollback.advice` | Should I roll back Prompt v8? |
| `revision.diff` | What changed between these revisions? |
| `decision.evidence` | Which evaluations support this decision? |
| `impact.of` | Show every artifact affected by this dataset. |
| `risk.ranking` | What is the biggest engineering risk right now? |
| `knowledge.topic` | What engineering knowledge exists about hallucinations? |
| `period.summary` | Summarize everything that happened this week. |
| `provider.failures` | Which provider causes the most failures? |
| `next.work` | What should my team work on next? |
| `latency.change` | Why did latency increase? |
| `cost.change` | Why did cost increase? |
| `decisions.unsupported` | What engineering decisions remain unsupported? |
| `knowledge.contradictions` | Show contradictions in our engineering knowledge. |
| `investigations.incomplete` | What investigations are still incomplete? |
| `system.state` | How is my system doing? |
| `artifact.explain` | Tell me about Checkout Agent. |

Anything else resolves to `unknown` and produces an honest refusal plus the questions the record can
answer.

## "Has this happened before?"

Worth calling out, because it is where Brok stops answering and starts **investigating**.

Ask it about a failure and Brok searches the evaluation record for earlier failures sharing an
agent, prompt or dataset with this one. If it finds a precedent, it reads the failed runs of *both*
and compares their recorded causes, then pulls in what the team decided afterwards and the
engineering memory behind that decision.

> *"Yes — this has happened before. Checkout Quality #1 failed 19 days ago against the same agent
> and dataset. Both failures recorded the same cause: 'Connection refused'. After that failure, the
> team recorded: 'Moved the endpoint behind the internal gateway.'"*

An identical recorded cause escalates the verdict from `attention` to `risk`, because a recurrence
is a different engineering problem from a coincidence. If there is no precedent, it says so plainly
and offers the diagnosis path instead.

## The eight Engineering Briefs

Brok also writes standing readings of the record. Each follows: *what happened → why → evidence →
impact → recommendation → next action.*

**Daily** · **Deployment** · **Incident** · **Prompt** · **Evaluation** · **Dataset** ·
**Knowledge** · **Architecture**

## Actions, not advice

Every recommendation ends in a real surface. Brok never invents a destination — it hands you back
into the platform at the exact place the answer came from:

Open the Forge Graph · Open the failure graph (already narrowed to the break) · View the execution
graph · Compare revisions · Open AI Git · Open Engineering Intelligence · Open Evolution · Open
Knowledge · Open the Registry · Open the evaluation · Open Analytics · Open Insights · Start an
investigation

## The workspace

Brok is a **workspace you travel to**, not a widget that follows you. Reachable from anywhere with
`Ctrl+.` or the header, and from every artifact, the Registry, Knowledge, the Forge Graph, the
Execution Graph, Evolution, AI Git and the dashboard — each carrying the context you were in.

The page is a conversation *plus* synchronized panels: engineering context, evidence, artifacts,
evaluations, decisions, knowledge, AI Git revisions, engineering memory, and the Forge Graph.
Clicking a referenced record or a graph node moves the whole workspace onto it.

While it works, it shows the investigation it is performing — *Reading the engineering record →
Resolving the subject → Searching for precedents → Composing the answer* — never "Thinking…".

## Why it has no language model

See [Deterministic Engineering Reasoning](/docs/deterministic-reasoning) for the full argument. The
short version: an LLM answers "has this happened before?" fluently whether or not a precedent
exists. A deterministic engine over real rows either finds it or says there is none — and that is
the only kind of answer worth building a workflow on.

See also: [Root Cause Explorer](/docs/root-cause-explorer) ·
[Engineering Intelligence](/docs/engineering-intelligence)
