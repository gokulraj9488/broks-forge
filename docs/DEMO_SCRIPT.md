# Demo Script — Brok's Forge

> **The Engineering Platform for AI Agents.**
>
> | | |
> |---|---|
> | **Document** | `docs/DEMO_SCRIPT.md` |
> | **Purpose** | A tight, repeatable 10–15 minute live demo (+ a 60-second elevator variant) |
> | **Audience** | The presenter; anyone running the product walkthrough |
> | **Companion** | [DEMO_DATASET_GUIDE.md](./DEMO_DATASET_GUIDE.md) (the seed data this script assumes) |

The demo tells one story: **register an agent → make it reproducible → evaluate it objectively →
compare and guard → explain and advise.** It maps exactly onto the README's "Verifying the build"
steps, so everything you click is real, not staged. Prepare the seed data from
[DEMO_DATASET_GUIDE.md](./DEMO_DATASET_GUIDE.md) *before* you present.

---

## Pre-flight (do this before the audience is watching)

1. **Bring up the stack.** `docker compose up --build` → wait for `actuator/health` = `UP`.
   - Web: `http://localhost:3000` · API: `http://localhost:8080` · Swagger:
     `http://localhost:8080/swagger-ui.html`.
2. **Dev SSRF allowances on** (so a localhost demo agent is reachable):
   `AGENT_HEALTH_ALLOW_PRIVATE_TARGETS=true` and `MODEL_ALLOW_PRIVATE_TARGETS=true` in `.env`.
   *(Say out loud during the demo that these are dev-only — production blocks private targets.)*
3. **Seed account & tenant ready:** a registered user, one organization (you're `OWNER`), one project.
4. **Demo agent endpoint running** (the simple HTTP echo/classifier from the dataset guide), pointed
   at by the agent you'll register, OR pre-register the agent and have one prior *failing* job so the
   root-cause/debugger beats have real data.
5. **Two browser states ready:** logged in, on the **Dashboard**. Have a second tab on Swagger for the
   "it's all real" moment.
6. **Dark mode on** (the product is dark-first; it looks best and matches the screenshots).

> Reset between runs: `docker compose down -v` wipes volumes for a clean reproducible demo.

---

## The 10–15 minute walkthrough (beat by beat)

Each beat: **[CLICK]** what to do · **[SAY]** the line that lands it.

### Beat 0 — Framing (45s)
- **[SAY]** "Teams shipping AI agents can't answer three questions with evidence: *is this version
  better? why did this run fail? what's it costing?* Brok's Forge is the system of record that
  answers them. Let me show you the loop."
- **[CLICK]** Land on the **Dashboard** — the platform roll-up (agents, jobs, benchmarks, trends).

### Beat 1 — Register an agent (1.5 min)
- **[CLICK]** Sidebar → **Agents** → **Register agent**. Fill name, framework (e.g. "custom REST" or
  "LangGraph"), language, **endpoint URL**, auth type.
- **[SAY]** "An agent here is *framework-agnostic* — it's described by metadata, not by any framework's
  types. The platform talks to it over its HTTP endpoint, so anything that speaks HTTP is evaluable.
  Frameworks and providers are text-backed enums, so adding a new one is code-only, no migration."
- **[CLICK]** Open the agent → show **Overview / Versions / Health / Credentials** tabs.

### Beat 2 — Version + credential + health (1.5 min)
- **[CLICK]** **Versions** → register a version with model + provider, **Activate immediately**.
- **[SAY]** "Versions are immutable deployment records with one active pointer — activate or rollback,
  exactly like a real release."
- **[CLICK]** **Credentials** → set an API-key credential.
- **[SAY]** "This is encrypted at rest with AES-256-GCM, *not* hashed — because we have to replay it to
  the agent. It's write-only: you'll never see it again, only a masked hint."
- **[CLICK]** **Health** → **Run check** → show status, latency, availability %.
- **[SAY]** "Every outbound call — health checks and evaluations — goes through an SSRF guard that
  blocks private and metadata targets. In dev I've allowed localhost; production wouldn't."

### Beat 3 — Make it reproducible: dataset + prompt (2 min)
- **[CLICK]** Sidebar → **Datasets** → create → **import** the demo CSV version → show **items** and
  **stats**.
- **[SAY]** "Datasets are *immutable, versioned*. Re-importing a corrected file makes a *new* version —
  it never edits an old one. That's what makes an evaluation reproducible months later: the inputs
  can't shift under it."
- **[CLICK]** Sidebar → **Prompts** → create → add a version with **`{{variables}}`** → **Activate** →
  add a second version → **Compare** / **Rollback**.
- **[SAY]** "Prompts are versioned templates with `{{variable}}` placeholders rendered per dataset
  item. Compare two wordings, roll back a bad change — and every evaluation pins the exact prompt
  version it used."

### Beat 4 — Evaluate (2.5 min) — the centerpiece
- **[CLICK]** Sidebar → **Evaluations** → **Evaluation profile** → create one (e.g. **EXACT_MATCH +
  LATENCY + JSON_VALID** with thresholds).
- **[SAY]** "A profile decouples *what to measure* from *what to run*, so the same rubric drives many
  jobs and benchmarks compare apples to apples."
- **[CLICK]** **Evaluation jobs** → create a job (agent version + dataset version + prompt version +
  profile) → **Run immediately**.
- **[SAY]** "`EvaluationJob` is the top-level object — it fans out into one run per dataset item, and
  one result per metric per run. It's shaped to scale to millions of rows: the hot path is
  insert-only, partitionable by job, and each job carries a precomputed summary so dashboards read one
  row, not millions. It runs synchronously today behind a *queue-ready seam* — async workers are a
  contained change later."
- **[CLICK]** Open the completed job → **summary** (pass-rate, cost, latency, tokens) → **runs** list →
  a run's **per-metric results**.
- **[SAY]** "Here's the objective answer to 'how good is this version' — per metric, per item, with
  cost and latency."

### Beat 5 — Compare & guard: benchmark + regression (1.5 min)
- **[CLICK]** Sidebar → **Benchmarks** → create → add **two** completed jobs as entries → view the
  **leaderboard**.
- **[SAY]** "Benchmarks are built *from* job summaries — no re-running. Compare agent-vs-agent,
  version-vs-version, prompt-vs-prompt, model-vs-model, dataset or profile. That's *'is the new version
  better?'* answered with evidence."
- **[CLICK]** Sidebar → **Regressions** → run a check (baseline job vs candidate job).
- **[SAY]** "A regression check is what you'd gate a deploy on in CI — did quality, latency, cost or
  tokens regress against a baseline?"

### Beat 6 — Explain & advise: the Phase-4 payoff (2.5 min)
- **[CLICK]** Sidebar → **Advisor** (project advisory). Then an agent's **Advisor** and a prompt's
  **Advisor**.
- **[SAY]** "This is *not* a chatbot. It's a recommendation engine of pure analyzers — Prompt, Model,
  Cost, Agent, RAG. Every recommendation tells you *why*, *what changed*, *how to fix*, *expected
  improvement*, *confidence* and *severity*. And it's computed on read — derived from live data every
  time — so it can never drift or go stale."
- **[CLICK]** Open a *failed* job → **Root cause**.
- **[SAY]** "A red result isn't actionable. Root-cause turns it into a diagnosis — timeout, HTTP error,
  empty output, malformed JSON, exact-match miss — with evidence and a recommendation. The engine is
  pure and deterministic, not an LLM guess."
- **[CLICK]** On a run → **Debug** → the **execution timeline** (`PROMPT → MEMORY → RETRIEVER → TOOLS →
  MODEL → PARSER → OUTPUT`).
- **[SAY]** "The AI Debugger reconstructs a per-stage timeline from persisted data. Notice MEMORY,
  RETRIEVER and TOOLS say **NOT_INSTRUMENTED** — the platform won't *fake* stages it can't yet see.
  When live tracing lands, those light up through a `TraceRecorder` seam that's already wired in as a
  no-op."
- **[CLICK]** Sidebar → **Knowledge** → browse a node (e.g. `TIMEOUT`) and its neighbours.
- **[SAY]** "Findings link into an Engineering Knowledge Graph of failure modes and fixes — seeded
  patterns with a counter that increments as the platform sees them recur. That counter is the seam
  for future learning."

### Beat 7 — Operability & "it's all real" (1 min)
- **[CLICK]** Sidebar → **Analytics** (cost/latency/token trends) → **Reports** → **export** a job
  (JSON/CSV/HTML).
- **[CLICK]** Switch to the Swagger tab → scroll the endpoint list.
- **[SAY]** "Every screen is backed by a documented REST API — 27 controllers, OpenAPI for all of it.
  Correlation IDs flow through every request and log line; the release ships Prometheus metrics and
  structured JSON logs. This is built like a production service, not a demo."

### Close (30s)
- **[SAY]** "That's the loop: register, make reproducible, evaluate objectively, compare and guard,
  then explain and advise — multi-tenant, provider-agnostic, secure by construction, and honest about
  what's shipped versus what's a seam for later."

---

## 60-second elevator demo

For when you have one minute and one screen. Have a completed job and one failed run pre-loaded.

1. **(0:00–0:10)** Dashboard. *"Brok's Forge answers the three questions AI teams can't: is this
   version better, why did it fail, what's it costing."*
2. **(0:10–0:30)** Open a completed **evaluation job** → summary + per-metric results. *"Objective
   evaluation: top-level Job fans out to one run per item, one result per metric — built to scale to
   millions, reproducible because the dataset and prompt versions are pinned and immutable."*
3. **(0:30–0:45)** Open the **benchmark leaderboard**. *"Compare versions/prompts/models from job
   summaries — 'is the new one better?' with evidence. A regression check gates the deploy."*
4. **(0:45–0:60)** Open **Root cause** on a failed run + the **NOT_INSTRUMENTED** debugger timeline.
   *"Red becomes a diagnosis and a fix, computed on read so it never drifts — and the debugger admits
   the stages it can't see yet instead of faking them. Provider-agnostic, multi-tenant, secure by
   construction."*

---

## Presenter notes & failure recovery

- **If an outbound call fails live**, it's a *feature*: open Root cause on it and show the diagnosis.
  ("This is exactly what the platform is for.")
- **If the agent endpoint is down**, fall back to a pre-run completed job and a pre-existing failed
  job — the read-side beats (summary, benchmark, advisor, root-cause, debugger, knowledge) need no
  live agent.
- **Don't promise** async workers, provider-direct OpenAI/Anthropic clients, distributed tracing, or
  test coverage as *shipped* — describe them as the next, already-seamed steps. Calibrated honesty is
  part of the pitch.
