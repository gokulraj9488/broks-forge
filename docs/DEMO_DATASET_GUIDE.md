# Demo Dataset Guide — Brok's Forge

> **The Engineering Platform for AI Agents.**
>
> | | |
> |---|---|
> | **Document** | `docs/DEMO_DATASET_GUIDE.md` |
> | **Purpose** | A compelling, reproducible demo data set: agent contract, dataset, prompt, profile, and how to provoke advice |
> | **Audience** | Anyone preparing the demo in [DEMO_SCRIPT.md](./DEMO_SCRIPT.md) |
> | **Grounding** | Metric names, providers and stage vocabulary match [MASTER_ARCHITECTURE.md](./MASTER_ARCHITECTURE.md) exactly |

The goal: a small, concrete task that makes every feature *visibly* earn its place — a clean pass
path *and* deliberately provokeable failures so the root-cause engine, advisor and AI Debugger have
real signal. The chosen task is **support-ticket intent classification with a structured JSON
output** — small, deterministic, and naturally exercises EXACT_MATCH, JSON_VALID and LATENCY.

---

## 1. The task

Classify a customer support message into one of a fixed label set and return a structured result.

- **Labels:** `BILLING`, `BUG`, `FEATURE_REQUEST`, `ACCOUNT`, `OTHER`.
- **Output contract:** a single JSON object `{"intent": "<LABEL>"}` — nothing else.

This task is ideal because: the expected output is exact (drives `EXACT_MATCH`), it's JSON (drives
`JSON_VALID`), it's cheap/fast (drives `LATENCY`/`COST`/`TOKEN_COUNT`), and wrong/over-chatty answers
produce *interpretable* failures the advisor and root-cause engine can explain.

---

## 2. The agent endpoint contract

Register an agent whose `endpoint_url` points at a tiny HTTP service implementing this contract. Any
language; the platform treats it as a black box over HTTP (that's the whole point of
`AgentEndpointInvoker`).

**Request** (what the platform sends — the rendered prompt + parameters):

```http
POST /invoke
Content-Type: application/json
Authorization: Bearer <decrypted agent credential>   # only if the agent has a credential

{
  "input": "<rendered prompt text for one dataset item>",
  "parameters": { "temperature": 0 }
}
```

**Response** (what the agent returns — the platform records output, latency, tokens, cost):

```json
{
  "output": "{\"intent\": \"BILLING\"}",
  "tokens": { "prompt": 78, "completion": 6, "total": 84 },
  "cost_micros": 120,
  "latency_ms": 240
}
```

Notes:
- The platform times the call end-to-end regardless; `tokens`/`cost_micros`/`latency_ms` enrich the
  run if the agent reports them.
- For a **reproducible demo**, back the endpoint with a deterministic rule (keyword match) rather than
  a live LLM, so passes and the deliberately-seeded failures are stable every run.
- A **reference echo/classifier** in ~40 lines (Flask/Express/FastAPI) is enough. Keep
  `MODEL_ALLOW_PRIVATE_TARGETS=true` in dev so a `localhost` endpoint passes the SSRF guard.

---

## 3. The dataset (CSV import — `input` + `expected` columns)

Datasets import as CSV or JSON and become an **immutable version**. Save this as
`support-intents-v1.csv` and import it as the dataset's first version. 10 rows: 8 are clean passes,
**rows 9–10 are crafted to fail** so the diagnostic features have something to chew on.

```csv
input,expected
"I was charged twice for my subscription this month.","{""intent"": ""BILLING""}"
"The export button throws a 500 error every time I click it.","{""intent"": ""BUG""}"
"Can you add dark mode to the mobile app?","{""intent"": ""FEATURE_REQUEST""}"
"I can't log in, it says my account is locked.","{""intent"": ""ACCOUNT""}"
"Why is my invoice higher than last month?","{""intent"": ""BILLING""}"
"The dashboard chart renders blank on Safari.","{""intent"": ""BUG""}"
"Please support CSV export in addition to JSON.","{""intent"": ""FEATURE_REQUEST""}"
"How do I change the email on my account?","{""intent"": ""ACCOUNT""}"
"hjkl asdf qwerty zzz","{""intent"": ""OTHER""}"
"My payment failed AND the app crashed on the receipt screen.","{""intent"": ""BILLING""}"
```

Why rows 9–10 are useful:
- **Row 9 (gibberish):** an under-specified prompt may return empty output or hedge → exercises
  `NON_EMPTY` / root-cause **empty output**.
- **Row 10 (ambiguous, two intents):** a chatty model may return prose or two labels, breaking
  `EXACT_MATCH` and possibly `JSON_VALID` → exercises root-cause **exact-match miss** / **JSON parse
  failure** and the **PromptAdvisor** (ambiguity).

> Tip: keep a **`support-intents-v2.csv`** (e.g. add 5 more rows or fix row 9's label) on hand to
> demo *immutability* — importing it creates **version 2**, leaving version 1's results intact and
> reproducible.

---

## 4. The prompt (with `{{variables}}`)

Create a prompt, add this as version 1, and **activate** it. The `{{message}}` placeholder is rendered
per dataset item from the `input` column.

**Prompt version 1 (the "good" one):**

```
You are a support-ticket classifier. Classify the message into exactly one label:
BILLING, BUG, FEATURE_REQUEST, ACCOUNT, OTHER.

Respond with ONLY a compact JSON object of the form {"intent": "<LABEL>"} and nothing else.

Message: {{message}}
```

**Prompt version 2 (the "bad" one — to demo Compare/Rollback and provoke the advisor):**

```
You are an extremely thorough, friendly, and detailed support assistant. Carefully read the
customer's message below and think step by step about what they might mean, considering billing,
bugs, feature requests, account issues, or something else entirely. Explain your reasoning in a
few sentences, then give your best guess. Be warm and conversational.

It is very important that you are helpful. Do not be unhelpful. Always be helpful.

Message: {{message}}
```

Version 2 is deliberately **bloated, redundant, contradictory in format intent** (asks for prose,
which breaks the JSON/exact-match contract), and over its variable/character budget — exactly the
shape the **PromptAdvisor** flags (bloat, redundancy, ambiguity, malformed-for-task). Evaluating
version 2 produces a *worse* job you can benchmark against version 1's job.

---

## 5. The evaluation profile

Create a profile combining structural and operational metrics. Recommended for this task:

| Metric (`EvaluationMetricType`) | Threshold | What it proves in the demo |
|---|---|---|
| `EXACT_MATCH` | output equals `expected` | Did it return exactly `{"intent":"<LABEL>"}`? |
| `JSON_VALID` | output is well-formed JSON | Did it honor the structured contract? |
| `LATENCY` | e.g. `< 2000 ms` | Operational metric → feeds analytics + LATENCY advisor |

Optional add-ons to enrich analytics/advisor: `NON_EMPTY` (catches row-9 empties), `TOKEN_COUNT`
(catches version-2 bloat), `COST` (feeds the CostAdvisor).

Name it something like **"Intent-Classification Rubric"** so it reads well on screen and in benchmarks
(`PROFILE_VS_PROFILE` compares apples to apples).

---

## 6. Provoking a useful root-cause / advisor / debugger result

The demo is most compelling when the diagnostic features have *real* failures to explain. Three
reliable provocations:

1. **Prompt-version regression (best for Advisor + Benchmark).**
   - Run job A with **prompt v1** (clean) and job B with **prompt v2** (bloated/prose).
   - Job B fails `EXACT_MATCH`/`JSON_VALID` on most rows.
   - **Benchmark** v1-job vs v2-job (`PROMPT_VS_PROMPT`) → leaderboard shows v1 winning.
   - **Regression check** baseline=v1-job, candidate=v2-job → quality regression.
   - **Prompt Advisor** on v2 → flags bloat, redundancy, contradiction/ambiguity, and the format
     mismatch, each with why / how to fix / expected improvement / confidence / severity.

2. **Failure rows (best for Root cause + Debugger).**
   - Even with prompt v1, rows 9–10 misbehave (empty / ambiguous) → those runs fail.
   - **Root cause** on the job → *empty output* and/or *exact-match miss* / *JSON parse failure*
     findings with evidence, linked into the knowledge graph (`EMPTY_OUTPUT`, `JSON_PARSE_FAILURE`,
     `EXACT_MATCH_MISS`).
   - **AI Debugger** on a failing run → `PROMPT`/`MODEL`/`PARSER`/`OUTPUT` populated;
     `MEMORY`/`RETRIEVER`/`TOOLS` honestly `NOT_INSTRUMENTED`.

3. **Latency/cost provocation (best for Analytics + Cost/Model advisors).**
   - Have the demo endpoint add an artificial delay (e.g. `sleep 2500ms`) for a couple of rows, or
     report inflated `cost_micros`/`tokens` for the prose answers.
   - `LATENCY`/`COST`/`TOKEN_COUNT` metrics fail → **Cost Advisor** flags wasted spend / token bloat;
     **Model Advisor** may suggest a cheaper/faster model (needs ≥ `ADVISOR_MIN_SAMPLES_FOR_COMPARISON`
     comparable jobs, default 3 — so run a few jobs first).

> **Knowledge-graph tie-in:** because findings reference stable knowledge keys (`TIMEOUT`,
> `EMPTY_OUTPUT`, `PROMPT_BLOAT`, `COST_SPIKE`, `MODEL_OVERKILL`, …), opening **Knowledge** and showing
> the matching node + its remediation closes the loop from *symptom → diagnosis → catalogued fix*.

---

## 7. Reproducibility checklist

- [ ] Demo endpoint is **deterministic** (rule-based, not a live LLM) so passes/failures are stable.
- [ ] `support-intents-v1.csv` imported as **dataset version 1**; keep v2 ready to demo immutability.
- [ ] **Prompt v1** active; **prompt v2** created (for Compare/Rollback/Advisor).
- [ ] **Profile** created (EXACT_MATCH + JSON_VALID + LATENCY, optionally NON_EMPTY/TOKEN_COUNT/COST).
- [ ] At least **3 completed jobs** exist before showing Model/Cost advisors
      (`ADVISOR_MIN_SAMPLES_FOR_COMPARISON`).
- [ ] One **clean job** (prompt v1) and one **worse job** (prompt v2) for the benchmark/regression beat.
- [ ] At least one **failed run** for root-cause + debugger.
- [ ] Dev SSRF allowances on (`AGENT_HEALTH_ALLOW_PRIVATE_TARGETS`, `MODEL_ALLOW_PRIVATE_TARGETS`) —
      and say so on screen, noting production blocks private targets.

Everything here is conceptually runnable against the platform as built: CSV import → immutable dataset
version, `{{variable}}` prompt rendering per item, profile-driven metrics, the `Job → Run → Result`
pipeline, and on-read advisor/root-cause/debugger. No fabricated capabilities.
