# FAQ

## What is Broks Forge, in one sentence?

An AI Engineering Operating System: it records the engineering act behind an AI system — what was
built, measured, decided and why — and reasons over that record.

## Is this an observability tool?

No. Observability answers *what happened*. Broks Forge answers *why the system is the way it is,
what evidence supports it, and what to do next*. They model different objects, and running both is
sensible. See [Why Observability Is Not Enough](/docs/why-observability-is-not-enough).

## Is Brok a chatbot? Does it use an LLM?

Brok is not a chatbot, and it contains **no language model**. It resolves your question to one of 25
engineering intents by deterministic phrase scoring, then composes the answer from real database
rows. Ask something the record cannot answer and it refuses.

This is deliberate: an LLM answers "has this happened before?" fluently whether or not a precedent
exists. See [Deterministic Engineering Reasoning](/docs/deterministic-reasoning).

## Do I have to use a specific framework?

No. Agents are registered by **HTTP endpoint**. LangChain, LlamaIndex, a custom FastAPI service, a
Spring Boot app, a serverless function — if it is callable over REST, it works. There is no SDK to
adopt.

## Does my data leave my infrastructure?

Self-hosted, no. Broks Forge calls your agent endpoint during an evaluation, and your configured
model providers if a metric needs one (`LLM_JUDGE`, `SEMANTIC_SIMILARITY`). Reasoning itself makes
no external calls, because there is no model to call.

Provider credentials are encrypted at rest and never returned by any read endpoint.

## Is it in my production request path?

No. Broks Forge is not a proxy or a gateway. It adds no latency to your traffic and cannot take your
application down.

## Is it open source? What does it cost?

Open source under Apache 2.0, self-hostable with Docker Compose. There is no licence fee and no
per-seat cost. You pay for the infrastructure you run it on and for any model calls your evaluations
make.

## How mature is it?

**Early.** It is a complete, tested system — 499 backend tests running against real PostgreSQL — but
it is a young project without the production track record or support organization of the commercial
tools it is compared with. The [Comparisons](/docs/comparisons) pages say this plainly.

## What is the minimum useful setup?

An agent, a dataset, a prompt, and one evaluation. That already produces observations, evidence, a
graph, an AI Git timeline and answerable questions.

The habit that matters most is writing one honest sentence in the notes field of every version. See
[Best Practices](/docs/best-practices).

## Why does it keep saying "unknown" instead of "healthy"?

Because nothing has measured that artifact. Absence of failure is not evidence of health, and the
platform refuses to imply otherwise. `unknown` is a distinct verdict state for this reason.

Run an evaluation and it becomes a real verdict.

## Can I use it without running evaluations?

You can register artifacts and get the Registry and the Forge Graph. But the reasoning layer is
built on evidence, so without evaluations almost everything reports `unknown` — accurately.

## What models and providers are supported?

Providers are pluggable adapters. Credentials are encrypted, health is checked, and failures are
attributed to the provider automatically. See [Extension Points](/docs/extension-points) for adding
one.

## How is this different from prompt versioning in LangFuse or LangSmith?

Both version prompts well. The difference is what surrounds a version: Broks Forge treats a
promotion as a **Decision** carrying the recorded rationale, links **Evidence** that covers that
specific revision, produces **Knowledge** when both exist, and flags the promotion as *unsupported*
when evidence is missing. See [vs LangFuse](/docs/vs-langfuse) and [vs LangSmith](/docs/vs-langsmith).

## Can I run it alongside my existing tools?

Yes, and for most teams that is the right setup: a tracing tool for production traffic, Broks Forge
for the engineering record. They model different objects over different time horizons.

## What is "AI Git"? Is it Git?

No. It is version control for **engineering reasoning** — revisions, promotions, rollbacks and the
rationale behind each change. Your prompt text may well be in Git too; that tells you the characters
changed. AI Git tells you what was promoted, why, on what evidence, and whether production is
currently on an older revision. See [AI Git](/docs/ai-git).

## What happens when an evaluation fails?

Runs are classified by *why* they failed — authentication, quota, rate limit, invalid model, network,
timeout, provider infrastructure, empty output, per-metric failure — because each has a different
fix. Then the [Root Cause Explorer](/docs/root-cause-explorer) can assemble the whole investigation:
chronology, four causal layers, evidence, precedents.

## Does it support human annotation or red-teaming?

No to both, currently. Promptfoo has a substantial red-teaming suite; LangFuse and LangSmith have
annotation queues. Those are real gaps and the comparison pages say so.

## Can I self-host in production?

Yes. See [Deployment](/docs/deployment). The reference deployment is AWS EC2 for the API with Vercel
for the frontend, but any Docker host works.

## How do I get data out?

Everything is available over the [REST API](/docs/rest-api) — there is no private API. Execution
graphs export as PNG, SVG or JSON.

## Who built it?

Broks Forge is built by Gokulraj as an open-source project. Source, issues and contributions:
[github.com/gokulraj9488/broks-forge](https://github.com/gokulraj9488/broks-forge).

## Where should I start?

[Getting Started](/docs/getting-started) to run it, or
[What is Broks Forge?](/docs/what-is-broks-forge) to understand it first.
