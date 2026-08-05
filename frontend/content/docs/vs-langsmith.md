# Broks Forge vs LangSmith

> LangSmith is LangChain's commercial platform for tracing, evaluating and monitoring LLM
> applications. It is mature, well-supported, and the default choice for many teams already building
> on LangChain or LangGraph. This page compares scope and philosophy. Verify details against
> [LangSmith's documentation](https://docs.smith.langchain.com/).

## In one line

**LangSmith** gives LangChain applications deep, first-class observability and evaluation.
**Broks Forge** is framework-agnostic and models the engineering decisions behind any AI system.

## What LangSmith does well

- **Deep LangChain / LangGraph integration** — tracing works essentially for free if you are already
  in that ecosystem, with visibility into every chain step and tool call.
- **Production monitoring** with cost, latency and error analytics.
- **Evaluation suite** — datasets, LLM-as-judge, pairwise comparison, regression testing in CI.
- **Prompt hub** — versioning, collaboration and a public prompt library.
- **Annotation queues** for human review and feedback collection.
- **Commercial support** and a substantial engineering organization behind it.

If your stack is LangChain, LangSmith's integration depth is very hard to match, and Broks Forge
does not try to.

## Where the scope differs

### Framework coupling

**LangSmith** is at its best inside the LangChain ecosystem. It supports other stacks via SDK and
OpenTelemetry, but the deepest value assumes LangChain abstractions.

**Broks Forge** registers an agent by **HTTP endpoint**. Anything callable over REST — LangChain,
LlamaIndex, a custom FastAPI service, a Spring Boot app, a serverless function — is a first-class
citizen, with no SDK to adopt and no framework to migrate to.

### What is remembered

Both version prompts. The difference is what happens around a version:

| Question | LangSmith | Broks Forge |
| --- | --- | --- |
| Which prompt version is live? | Yes | Yes |
| What text changed? | Yes | Yes (field-level diff) |
| **Why was it promoted?** | Not modelled | Recorded rationale → Engineering Memory |
| **What evidence covers this revision?** | Runs exist, not linked as support | Evidence, linked to the decision |
| **Which promotions have no evidence?** | Not modelled | A first-class query |
| **Is production on an older revision?** | — | Shown explicitly as a rollback |

### What happens after a failure

**LangSmith** gives you the trace, the inputs and outputs, and the error. Excellent for
understanding *this* failure.

**Broks Forge** assembles an [investigation](/docs/root-cause-explorer): a dated chronology of the
engineering around the failure, the cause at four depths, every earlier failure on the same ground
with what was decided afterwards, and the blast radius.

## Side by side

| | LangSmith | Broks Forge |
| --- | --- | --- |
| Best with | LangChain / LangGraph | Any HTTP-callable system |
| Production tracing | **Yes, core strength** | No |
| Instrumentation | SDK / OTel | None — endpoint registration |
| Evaluation | Yes, comprehensive | Yes, 14 metric types, pinned configs |
| Pairwise comparison | Yes | Via benchmarks |
| Human annotation | Yes | No |
| Prompt hub / sharing | Yes | No |
| Decisions & evidence | No | **Yes** |
| Engineering memory | No | **Yes** |
| Artifact dependency graph | No | **Yes** |
| Precedent search | No | **Yes** |
| Root-cause investigation | No | **Yes** |
| Grounded Q&A | LLM-assisted | **Deterministic, no LLM** |
| Licensing | Commercial (free tier) | Open source, self-hosted |
| Maturity | High | Early |

## Philosophy

**LangSmith optimises the LangChain development loop** — build a chain, trace it, evaluate it, ship
it, monitor it. Within that loop it is excellent and highly integrated.

**Broks Forge optimises the engineering record** — what was decided, on what evidence, and why. It
assumes you already have a way to build and run your system, and concerns itself with whether your
team can explain and defend it in six months.

The clearest test: LangSmith answers *"what did this chain do?"* better than Broks Forge ever will.
Broks Forge answers *"why is this chain configured this way, and has this failure happened before?"*,
which LangSmith does not model.

## Which to choose

**Choose LangSmith if** you are building on LangChain, want the deepest possible tracing with the
least effort, need human annotation or pairwise evaluation, or want a commercially supported product.

**Choose Broks Forge if** you are framework-agnostic or multi-framework, need to keep decisions and
their evidence, want precedent search and assembled investigations, or need to self-host with no
per-seat cost.

**Run both if** you are a LangChain shop that also needs a durable engineering record. There is no
conflict — one observes execution, the other records engineering.

See also: [Comparisons Overview](/docs/comparisons) ·
[Deterministic Engineering Reasoning](/docs/deterministic-reasoning)
