# Broks Forge vs Helicone

> Helicone is an open-source LLM observability platform and gateway, best known for one-line
> integration via a proxy. It is fast to adopt and strong at cost control and caching. This page
> compares scope, not quality. Verify details against
> [Helicone's documentation](https://docs.helicone.ai/).

## In one line

**Helicone** sits in the request path and observes, caches and controls your LLM traffic.
**Broks Forge** sits outside the request path and records the engineering behind the system.

## What Helicone does well

- **One-line integration** — change a base URL and requests are logged. Genuinely the lowest
  adoption cost in this comparison set.
- **Gateway capabilities** — caching, retries, fallbacks and rate limiting at the proxy.
- **Cost tracking** per request, user, model and custom property, with budget alerting.
- **Prompt and session tracking** over live traffic.
- **User-level analytics** — spend and usage per end user, which matters for usage-based pricing.
- **Open source and self-hostable.**

If your priority is *"see and control what my LLM calls cost, today, with almost no work"*, Helicone
is hard to beat and Broks Forge offers nothing comparable.

## The structural difference

This comparison has the clearest architectural distinction of the set.

**Helicone is in the request path.** It is a proxy: your traffic flows through it, which is what
makes caching, rate limiting and fallbacks possible. It sees every request, and nothing else.

**Broks Forge is not in the request path at all.** It calls your agent's endpoint during an
evaluation, but it does not intermediate your production traffic. It sees engineering acts —
registrations, versions, promotions, evaluations, rollbacks — and reasons about them.

```
   HELICONE                          BROKS FORGE

   your app                          your engineering
      │                                    │
      ▼                                    ▼
   ┌─────────┐                      ┌──────────────┐
   │Helicone │ cache, limit,        │ artifacts    │
   │ proxy   │ log, fallback        │ revisions    │
   └────┬────┘                      │ evaluations  │
        ▼                           │ decisions    │
   provider API                     │ evidence     │
                                    │ knowledge    │
   sees: every request              └──────────────┘
   sees not: why anything            sees: why the system
             is configured                 is the way it is
             that way                sees not: production traffic
```

Neither can do the other's job, and neither is trying to.

## What each can answer

| Question | Helicone | Broks Forge |
| --- | --- | --- |
| What did this request cost? | **Yes** | No |
| Which user is driving our spend? | **Yes** | No |
| Can I cache this call? | **Yes** | No |
| Can I fall back to another provider? | **Yes** | No |
| Which prompt version is promoted, and why? | No | **Yes** |
| What evidence supports the current config? | No | **Yes** |
| Has this failure happened before? | No | **Yes** |
| What breaks if I change this dataset? | No | **Yes** |
| Which decisions have no evidence? | No | **Yes** |

## Side by side

| | Helicone | Broks Forge |
| --- | --- | --- |
| Deployment model | Proxy / gateway in the request path | Standalone platform, outside it |
| Integration cost | One line (base URL) | Register artifacts; Docker stack |
| Production request logging | **Yes, core strength** | No |
| Caching, rate limiting, fallbacks | **Yes** | No |
| Per-user cost analytics | **Yes** | No |
| Evaluations against datasets | Limited | **Yes, 14 metric types** |
| Versioned artifacts with rationale | Prompts, lightly | **Yes, all, with memory** |
| Decisions & evidence | No | **Yes** |
| Dependency graph | No | **Yes** |
| Root-cause investigation | No | **Yes** |
| Grounded Q&A | No | **Yes, deterministic** |
| Latency added to your requests | Some (it is a proxy) | **None** |

## Philosophy

**Helicone optimises the request.** Make each call cheaper, faster, more reliable, and visible. Its
value scales with traffic volume.

**Broks Forge optimises the decision.** Make each engineering choice defensible, remembered and
learnable-from. Its value scales with how long the system lives and how many people touch it.

A team with high traffic and few engineering changes gets more from Helicone. A team with modest
traffic and constant prompt, model and dataset churn gets more from Broks Forge. Most serious teams
are both.

## Which to choose

**Choose Helicone if** you need immediate cost visibility, caching, rate limiting or provider
fallbacks, and want the lowest possible integration effort.

**Choose Broks Forge if** you need an engineering record: why the system is configured as it is,
what evidence supports it, and what happened last time this broke.

**Run both** — they do not overlap and they do not conflict. Helicone in the request path for
control and cost; Broks Forge outside it for engineering memory and investigation.

See also: [Comparisons Overview](/docs/comparisons) ·
[Why Observability Is Not Enough](/docs/why-observability-is-not-enough)
