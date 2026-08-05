# Forge Graph

The Forge Graph is your AI organization as a connected system: every engineering artifact and the
real relationships between them.

## Why a graph

AI systems are graphs, not tables. A prompt is used by an agent, which is measured by an evaluation,
which uses a dataset, which supports a decision, which produces knowledge that bears on three other
artifacts.

Every interesting engineering question is a traversal:

- *What would break if I changed this dataset?* — walk the dependents.
- *What does this evaluation actually cover?* — walk the dependencies.
- *Where is this prompt reused?* — walk the edges.
- *What is this failure connected to?* — walk the neighbourhood.

None of these are answerable in a list view, which is why most tools cannot answer them at all.

```
            ┌───────────┐         ┌───────────┐
            │  Provider │         │  Dataset  │
            └─────┬─────┘         └─────┬─────┘
                  │ serves              │ measures
                  ▼                     ▼
   ┌────────┐  ┌───────┐         ┌────────────┐
   │ Prompt │─►│ Agent │────────►│ Evaluation │
   └────────┘  └───────┘ measured└──────┬─────┘
      used by              by           │ produced
                                        ▼
                                 ┌─────────────┐
                                 │ OBSERVATION │
                                 └──────┬──────┘
                                        ▼
                        DECISION ──► KNOWLEDGE
```

## The reasoning overlay

Toggle **Show reasoning** and the derived objects — observations, claims, decisions, evidence,
knowledge — appear as nodes attached to the artifacts they came from.

This is the part with no equivalent elsewhere: you can literally *see the thinking layered over the
system*, rather than having it buried in pages. Selecting a knowledge node opens its own page.

## Colour carries one meaning

A rule inherited from the design language and worth stating, because it is what keeps the graph
readable: **structural hues say what a thing is; the verdict palette says how it is going.** The two
are never mixed. A node's shape and colour tell you it is a prompt; its state tells you whether it
is healthy, needs attention, is at risk, has failed, or is simply not yet known.

## Focus

Click any node to focus its neighbourhood. Arriving from elsewhere — an artifact page, a Brok
answer, an investigation — focuses the graph on the node the answer was about, via
`/knowledge?focus=<node-id>`.

Node ids are stable and composite: `prompt:<uuid>`, `evaluation:<uuid>`,
`decision:prompt-version:<uuid>`. The same id identifies the node everywhere in the product, which
is why a Brok answer and a graph selection stay in step.

## Where it appears

- **`/knowledge`** — the full graph.
- **Beside Brok** — a compact graph in the workspace rail, following whatever the conversation is
  about.
- **Inside an investigation** — the same compact graph, following the timeline event or record you
  select.
- **From any artifact** — *See in graph* on the Evolution tab.

## Graph versus Execution Graph

Two different graphs, two different jobs — a common point of confusion:

| | Forge Graph | [Execution Graph](/docs/execution-graph) |
| --- | --- | --- |
| Shows | The whole system's structure | One evaluation run's runtime path |
| Nodes are | Artifacts and reasoning objects | Pipeline stages |
| Lifetime | As long as the artifacts exist | One run |
| Answers | What is connected to what | Where the chain broke |

See also: [Evolution](/docs/evolution) · [Registry](/docs/registry) ·
[Engineering Intelligence](/docs/engineering-intelligence)
