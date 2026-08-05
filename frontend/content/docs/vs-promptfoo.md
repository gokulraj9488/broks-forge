# Broks Forge vs Promptfoo

> Promptfoo is an open-source, developer-first tool for testing and red-teaming LLM prompts and
> applications. It is fast, config-driven, CI-friendly and genuinely pleasant to use. This page
> compares scope, not quality. Verify details against
> [Promptfoo's documentation](https://www.promptfoo.dev/docs/intro/).

## In one line

**Promptfoo** tests prompts from your terminal and your CI pipeline, declaratively.
**Broks Forge** keeps a persistent engineering record and reasons over it.

## What Promptfoo does well

- **Declarative config** — a YAML file describes prompts, providers, test cases and assertions, and
  lives in your repo next to the code.
- **Fast local loop** — `promptfoo eval` runs in seconds; the web viewer shows a side-by-side matrix.
- **Broad provider support** and easy multi-model comparison in a single run.
- **Rich assertions** — deterministic checks, model-graded rubrics, custom JavaScript and Python.
- **CI-native** — trivially wired into a pull request check.
- **Red-teaming and security scanning** for jailbreaks, PII leakage and prompt injection — a
  substantial capability Broks Forge does not have at all.

For "does this prompt change break anything?" as a pre-merge gate, Promptfoo is excellent, and its
red-teaming suite has no equivalent here.

## Where the scope differs

The essential difference is **memory**.

**Promptfoo is stateless by design.** Each run is an execution of a config file. Results can be
shared and stored, but the tool's centre of gravity is the run you just did. That statelessness is
a feature: it makes it fast, reproducible from a file, and easy to reason about.

**Broks Forge is a persistent record.** An evaluation is not a command you ran; it is an event in
the engineering history of an artifact. It becomes an Observation, potentially Evidence, connects to
the artifacts it measured in the graph, and becomes searchable precedent for a failure six months
later.

That is why questions like *"has this happened before?"* and *"which of our promotions have no
evidence?"* are natural in one tool and unanswerable in the other. They require history that
persists across runs, across artifacts, and across people.

## Where the config lives

| | Promptfoo | Broks Forge |
| --- | --- | --- |
| Source of truth | `promptfooconfig.yaml` in your repo | The platform's own record |
| Versioning | Git, alongside code | AI Git, with rationale and promotion state |
| Who runs it | A developer, or CI | Anyone in the organization, via the app or API |
| Result lifetime | The run, plus whatever you export | Permanent, and linked |
| Multi-user | Via Git and shared links | Organizations, projects, roles, membership |

Neither is wrong. Config-in-repo is the right answer for a pre-merge gate. A persistent multi-user
record is the right answer for organizational memory.

## Side by side

| | Promptfoo | Broks Forge |
| --- | --- | --- |
| Local CLI | **Yes, core strength** | No (REST API and web app) |
| CI gate | **Yes, trivial** | Via the REST API |
| Multi-model matrix | **Yes, excellent** | Via benchmarks |
| Red-teaming / security scanning | **Yes** | **No** |
| Custom assertions in code | **Yes** | `CUSTOM` metric type |
| Persistent history across runs | Limited | **Yes, permanent** |
| Artifacts as first-class objects | No | **Yes** |
| Decisions & evidence | No | **Yes** |
| Engineering memory | No | **Yes** |
| Dependency graph | No | **Yes** |
| Precedent search | No | **Yes** |
| Root-cause investigation | No | **Yes** |
| Multi-user, roles, tenancy | No | **Yes** |
| Setup cost | Minutes | Docker stack |

## Philosophy

**Promptfoo optimises the inner loop.** Change a prompt, run the tests, see the matrix, commit. The
faster that loop, the better the tool — and it is very fast.

**Broks Forge optimises the outer loop.** Not "did this change break anything?" but "why is the
system the way it is, what evidence supports it, and has this failure happened before?". Those
questions are asked weeks and months apart, by people who were not there for the original change.

## Which to choose

**Choose Promptfoo if** you want a fast local testing loop, config that lives in your repo, a
pre-merge CI gate, or red-teaming and security scanning.

**Choose Broks Forge if** you need a durable multi-user engineering record, decisions with evidence,
memory of why things are the way they are, or assembled investigations with precedent.

**Run both** — this is arguably the most natural pairing in this comparison set. Promptfoo as the
pre-merge gate on prompt changes; Broks Forge as the engineering record of what was promoted, why,
and what happened next. They do not overlap much.

See also: [Comparisons Overview](/docs/comparisons) ·
[Evaluations & Metrics](/docs/evaluations)
