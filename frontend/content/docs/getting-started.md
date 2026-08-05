# Getting Started

This walkthrough takes you from an empty machine to a real engineering record you can reason over.
Budget about fifteen minutes, most of it waiting on the first Docker build.

## Prerequisites

- **Docker Desktop** with Docker Compose v2. That is the only hard requirement — Postgres, Redis,
  the API and the web app all run in containers.
- Optional, for local development without Docker: **JDK 21**, **Maven**, **Node.js 20+**.

## 1. Run the stack

```bash
git clone https://github.com/gokulraj9488/broks-forge.git
cd broks-forge

# Configure environment
cp .env.example .env

# Set the two REQUIRED secrets in .env — the API fails fast without them
openssl rand -base64 48   # -> JWT_SECRET=<value>
openssl rand -base64 32   # -> ENCRYPTION_KEY=<value>   (must be 32 bytes)

# Build and start everything
docker compose up --build
```

The API fails fast on a missing secret rather than starting in an insecure default state. That is
deliberate.

Once the stack is healthy:

| Service | URL |
| --- | --- |
| Web app | `http://localhost:3000` |
| API | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Health | `http://localhost:8080/actuator/health` |

Tear down with `docker compose down`, adding `-v` to wipe the data volumes.

## 2. Create an account, an organization and a project

Register at `http://localhost:3000/register`. Everything in Broks Forge is scoped to an
**organization**, and most work is scoped to a **project** inside it — so create one of each. Your
account becomes the organization's owner.

## 3. Register your first artifacts

Broks Forge reasons about *artifacts*. Register at least an agent and a dataset; a prompt is
strongly recommended, because it is what gives you an AI Git history to reason over.

**Agent** — an AI system you want to evaluate. Broks Forge is framework-agnostic: an agent is
registered by its HTTP endpoint, so anything you can call over REST works, whether it is LangChain,
LlamaIndex, a custom FastAPI service or a plain function behind a route.

**Dataset** — your ground truth. Import CSV or JSON with `input` and `expected_output` columns.
Datasets are versioned; importing again creates a new version rather than overwriting.

**Prompt** — versioned instruction text. Create a version, add notes explaining *why* you wrote it
that way, and activate it. Those notes become [Engineering Memory](/docs/engineering-memory) — this
is the single highest-value habit in the whole platform.

**Provider** *(optional)* — API credentials for a model provider. Credentials are encrypted at rest.

## 4. Run an evaluation

Create an evaluation job, pin it to the agent, dataset and prompt, and run it.

An evaluation is a **reproducible measurement**: the configuration is pinned at creation time, so
re-reading the result months later still tells you exactly what was measured. Each dataset item
becomes a **run** with its own output, latency, cost, HTTP status and metric results.

When it finishes, you have crossed the line that matters: the platform now holds **evidence**.

## 5. Read the engineering record

This is the part that has no equivalent in an evaluation tool. Look at what appeared without anyone
authoring it:

- **Registry** — every artifact and every derived knowledge object, in one catalog.
- **The artifact's Intelligence tab** — observations, claims, decisions, evidence, knowledge and
  memory for that artifact.
- **Evolution tab** — what it depends on, what depends on it, and the blast radius of a change.
- **AI Git** (inside Evolution) — the revision timeline with promotions, rollback readiness, and the
  rationale you recorded.
- **Forge Graph** (`/knowledge`) — the whole system as a connected map. Toggle *Show reasoning* to
  layer the derived objects onto the artifacts they came from.

## 6. Ask Brok

Open **Brok** from the header, or press `Ctrl+.` from anywhere. Try:

- *"How is my system doing?"*
- *"What should my team work on next?"*
- *"Why was <your prompt> promoted?"* then, without restating the subject, *"Show me the evidence."*
  and *"Should I promote it?"*
- *"What engineering decisions remain unsupported?"*

Two things to notice. Every statement says how it is known — *derived*, *inferred*, *suggested* or
*unknown*. And follow-ups inherit the subject, so a conversation reads like a conversation.

Ask something the record cannot answer — *"What is the capital of France?"* — and Brok will say so
and offer the questions it *can* answer. That refusal is the feature.

See [Brok](/docs/brok) for the full list of questions it answers.

## 7. Investigate a failure

If an evaluation failed — point an agent at an unreachable endpoint if you want to force one — open
it and click **Investigate**.

The [Root Cause Explorer](/docs/root-cause-explorer) assembles the whole investigation: a dated
chronology, the cause at four depths, the evidence chain, the AI Git chain, engineering memory, and
any earlier failure on the same ground. If the same ground has failed before, it will tell you when,
and whether the recorded cause was identical.

## What to do next

| If you want to… | Read |
| --- | --- |
| Understand the object model | [Core Concepts](/docs/core-concepts) |
| Understand the layering | [The Five Layers](/docs/the-five-layers) |
| See the loop end to end | [The Engineering Workflow](/docs/engineering-workflow) |
| See it applied to real problems | [Examples](/docs/examples) |
| Get the most out of it | [Best Practices](/docs/best-practices) |
| Build against the API | [REST API](/docs/rest-api) |
| Run it for real | [Deployment](/docs/deployment) |
