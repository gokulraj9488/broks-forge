# Knowledge

Knowledge is a **durable engineering fact** that emerged from real decisions and real evidence. It is
what your team has proven, as distinct from what it has merely logged.

> *"This prompt's canonical revision is v3, backed by 4 evaluations."*

## The rule that makes it trustworthy

Knowledge exists **only where a genuine decision and genuine supporting evidence both exist.**

That single rule is why knowledge in Broks Forge can be relied on:

- Promote a prompt but never evaluate it → **no knowledge**. Instead the platform reports an
  *unsupported decision*.
- Evaluate an artifact but never promote anything → **no knowledge**. You have observations, not a
  position.
- Do both → knowledge appears, linked to the decision and the evidence it rests on.

There is no way to add knowledge by hand, which means there is no way to add knowledge that is not
backed by something real.

## Knowledge versus a wiki

| | A wiki page | Broks Forge Knowledge |
| --- | --- | --- |
| Created by | A person, in a separate act | Derived from the engineering act itself |
| Goes stale | Silently, and usually within weeks | Cannot — it is computed on read |
| Cites evidence | If someone remembered to | Always, structurally |
| Contradicts reality | Frequently, and invisibly | Surfaced as a *contradiction* |
| Survives the author leaving | Rarely useful | Fully |

The failure mode of documentation is that it is a *copy* of the truth, and copies drift. Knowledge is
not a copy — it is a reading.

## Contradictions

Because knowledge is derived rather than asserted, the platform can notice when the record disagrees
with itself.

A **contradiction** is a claim that an artifact's canonical revision is settled, sitting beside
failing evaluations of that same artifact. Broks Forge reports it as an *inference*, never as a
fact — a failing evaluation does not automatically invalidate a promotion, but it does mean the
claim should not be read as settled while it stands.

Ask Brok *"Show contradictions in our engineering knowledge"* to see them. Leaving one unresolved is
a choice; not knowing about it is an accident.

## Unsupported decisions

The mirror image, and one of the most valuable things the platform surfaces: a promotion with no
evaluation behind it.

An unsupported decision cannot be defended in review and cannot be safely reversed, because nobody
knows what it was worth. Ask *"What engineering decisions remain unsupported?"* to list them.

Most teams have more of these than they expect. Finding them is usually the fastest available
improvement to engineering confidence.

## Where knowledge lives

- **[Registry](/docs/registry)** — switch to the Knowledge scope to browse everything derived.
- **A dedicated page per object** — every knowledge object has its own URL showing what created it,
  the decision and evidence behind it, and every artifact it affects.
- **[Forge Graph](/docs/forge-graph)** — toggle *Show reasoning* and knowledge appears as nodes
  attached to the artifacts it came from.
- **[Brok](/docs/brok)** — *"What engineering knowledge exists about X?"* searches it.
- **The Knowledge Brief** — one of the eight standing briefs, reading the knowledge estate as a whole.

## Knowledge in the object model

```
   DECISION  ────────┐
   "v3 was promoted" │
                     ├────► KNOWLEDGE
   EVIDENCE  ────────┘      "v3 is canonical,
   "4 evaluations            backed by 4 evaluations"
    covered it"                      │
                                     ▼
                            links to every artifact
                            it bears on
```

Both inputs are required. This is enforced in the derivation, not by convention.

See also: [Engineering Intelligence](/docs/engineering-intelligence) ·
[Core Concepts](/docs/core-concepts) · [Engineering Memory](/docs/engineering-memory)
