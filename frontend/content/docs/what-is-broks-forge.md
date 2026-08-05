# What is Broks Forge?

**Broks Forge is an AI Engineering Operating System.** It records the engineering act behind an AI
system — what was built, what was measured, what was decided, and why — and then reasons over that
record to answer engineering questions.

It is not an evaluation tool, an observability dashboard, or an LLM tracing platform. Those tools
answer *what happened*. Broks Forge is built to answer *why it happened, what it means, and what to
do next*.

## The one-paragraph version

You register your AI artifacts — agents, prompts, datasets, providers, models. You evaluate them
against real data. From that real work, Broks Forge derives an engineering record: observations of
what was measured, claims about what is true, decisions that were taken, evidence that supports
them, and durable knowledge that emerges. It versions every artifact with the rationale behind each
change (AI Git), maps the whole system as a graph (Forge Graph), and puts two reasoning applications
on top: **Brok**, an engineering partner that answers questions from that record, and the **Root
Cause Explorer**, which assembles a complete investigation when something fails.

## Who it is for

| Role | What it gives them |
| --- | --- |
| **AI / ML engineers** | Reproducible evaluations, a versioned history of every prompt and agent, and an investigation workspace instead of a log search. |
| **Staff / lead engineers** | A single engineering record they can reason over — including precedent: "has this failed before, and what did we do?" |
| **Engineering managers & CTOs** | Evidence behind decisions. Which promotions are defensible, which are being carried on faith, and what the system is currently unable to prove. |
| **Teams inheriting an AI system** | Engineering Memory. The reasoning behind the current state survives the person who made it. |

## Why it exists

Teams shipping AI systems accumulate decisions faster than they can record them. A prompt is
promoted on a Tuesday because it "seemed better." A dataset is regenerated. A model is swapped for
cost. Six weeks later an evaluation fails, and nobody can reconstruct which of those changes
mattered — because the reasoning lived in a chat thread, a pull request comment, or somebody's head.

Observability tools do not solve this. They record traces of execution with excellent fidelity, but
a trace cannot tell you that a prompt was promoted without evidence, that a claim contradicts the
evaluations behind it, or that this exact failure happened nineteen days ago and the team rolled
back. Those are **engineering** facts, and they need an engineering data model.

Broks Forge is that data model, plus the reasoning layer that makes it useful.

## What makes it different

**It derives, it does not ask you to author.** No one writes an "observation" or a "decision" by
hand. Promoting a prompt version *is* a decision; running an evaluation against it *is* evidence.
The reasoning objects are computed from work you were doing anyway.

**Nothing is fabricated.** The reasoning layer is a deterministic engine over real database rows,
not a language model summarising documentation. If the record cannot answer a question, the answer
is "the engineering record cannot answer that" — followed by the questions it *can* answer.

**Every statement declares how it is known.** Each one is marked *derived* (read directly from
records), *inferred* (a causal reading that could be wrong), *suggested* (a recommendation), or
*unknown*. Confidence is a three-step verbal ladder — *consistent with*, *likely*, *near-certain* —
never a fabricated percentage.

**Absence is never dressed up as health.** An artifact nobody has evaluated is reported as unproven,
not as passing.

## The shape of the system

```
                    ENGINEERING APPLICATIONS
        Brok (partner)  ·  Root Cause Explorer (investigation)
                              |
                        FORGE GRAPH
              the living map of the whole system
                              |
                          AI GIT
          revisions, promotions, rollbacks, rationale
                              |
                         REGISTRY
        one catalog of artifacts and derived knowledge
                              |
                       FORGE KERNEL
            the invisible foundation everything sits on
```

Read [The Five Layers](/docs/the-five-layers) for what each one owns.

## What it is not

- **Not a chatbot with a dashboard.** Brok has no language model behind it. It resolves an
  engineering intent and composes an answer from rows that exist.
- **Not a tracing tool.** There is an Execution Graph, but it is reconstructed from an evaluation's
  own recorded runs to answer "where did the chain break?", not to sample production traffic.
- **Not a replacement for your framework.** Agents are registered by endpoint. Broks Forge is
  framework-agnostic and does not ask you to adopt a SDK to be useful.
- **Not a benchmark leaderboard.** Evaluations exist to produce evidence for decisions about *your*
  system, not to rank models in the abstract.

## Where to go next

- New to the category → [The AI Engineering Operating System](/docs/ai-engineering-operating-system)
- Want to run it → [Getting Started](/docs/getting-started)
- Want the object model → [Core Concepts](/docs/core-concepts)
- Comparing tools → [Comparisons Overview](/docs/comparisons)
- Building on it → [Architecture Overview](/docs/architecture)
