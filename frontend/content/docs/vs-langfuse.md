# Broks Forge vs LangFuse

> LangFuse is a well-regarded open-source LLM engineering platform with a strong tracing story, an
> active community and production deployments at scale. This page describes differences in scope and
> philosophy, not quality. Verify capability details against
> [LangFuse's own documentation](https://langfuse.com/docs) — products change.

## In one line

**LangFuse** observes what your LLM application did, in production, at volume.
**Broks Forge** records the engineering act behind the system and reasons over it.

## What LangFuse does well

- **Production tracing** — nested traces and spans across chains, agents and tool calls, with strong
  SDK coverage for Python and JavaScript.
- **Cost and latency analytics** over real traffic.
- **Prompt management** with versioning and deployment labels.
- **Datasets and evaluations**, including LLM-as-judge and human annotation queues.
- **Session and user grouping** for debugging a specific conversation.
- **Mature open source** — self-hostable, widely deployed, actively developed.

If your primary need is *"instrument my LLM app and see what it is doing in production"*, LangFuse
is a strong, proven choice and Broks Forge does not replace it.

## Where the scope differs

The clearest way to see it: both tools version prompts, but they mean different things by it.

**LangFuse** versions the prompt so you can deploy and roll back the text, and see which version
produced which trace. This is prompt management, and it works well.

**Broks Forge** treats a promotion as a **[Decision](/docs/core-concepts)** — a first-class object
carrying the rationale recorded at the time, linked to the **Evidence** that covers that specific
revision, producing **Knowledge** when both exist, and surfaced as an *unsupported decision* when
evidence is missing.

So both can tell you *v3 is live*. Only one can tell you *v3 was promoted for this recorded reason,
covered by these four evaluations, and here is the contradiction between that claim and last night's
failure*.

## Side by side

| | LangFuse | Broks Forge |
| --- | --- | --- |
| Primary object | Trace / observation | Engineering artifact + derived reasoning |
| Production request tracing | **Yes, core strength** | No — not a production tracer |
| SDK instrumentation | Python, JS, OpenTelemetry | None required; agents registered by HTTP endpoint |
| Prompt versioning | Yes, with deploy labels | Yes, as revisions with rationale and rollback state |
| Datasets & evaluation | Yes | Yes, with pinned reproducible configuration |
| LLM-as-judge | Yes | Yes (`LLM_JUDGE`, plus 13 other metric types) |
| Human annotation | Yes | No |
| Decisions as objects | No | **Yes** |
| Evidence linked to decisions | No | **Yes** |
| Engineering memory ("why?") | No | **Yes, verbatim** |
| Artifact dependency graph | No | **Yes (Forge Graph)** |
| Precedent search over failures | No | **Yes** |
| Assembled root-cause investigation | No | **Yes (four causal layers)** |
| Grounded Q&A | LLM-assisted features | **Deterministic, no LLM** |
| Maturity | High | Early |

## Philosophy

**LangFuse is instrumentation-first.** You add an SDK, traffic flows in, and the platform's value
scales with volume. The model is: capture everything, analyse afterwards.

**Broks Forge is record-first.** Value comes from the *structure* of a small number of high-value
engineering acts — a promotion, an evaluation, a rollback — not from the volume of requests. Ten
evaluations and five promotions with recorded reasons produce a genuinely useful engineering record.
Ten million traces produce excellent observability and no answer to "why is it like this?".

A second difference worth naming: Broks Forge's reasoning layer contains **no language model**. Ask
it something the record cannot support and it refuses. See
[Deterministic Engineering Reasoning](/docs/deterministic-reasoning).

## Which to choose

**Choose LangFuse if** your priority is production observability for an LLM application, you want
mature SDK instrumentation, you need session-level debugging or human annotation workflows, or you
need a proven tool with a large community today.

**Choose Broks Forge if** your problem is that nobody can explain why the system is configured the
way it is, that failures repeat because precedent is lost, or that promotions cannot be defended in
review.

**Run both if** you have production traffic to observe *and* an engineering record to keep. They
model different objects over different time horizons, and neither substitutes for the other.

See also: [Comparisons Overview](/docs/comparisons) ·
[Why Observability Is Not Enough](/docs/why-observability-is-not-enough)
