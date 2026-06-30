# Screenshot Guide — Brok's Forge

> **The Engineering Platform for AI Agents.**
>
> | | |
> |---|---|
> | **Document** | `docs/SCREENSHOT_GUIDE.md` |
> | **Purpose** | Exactly which screens to capture for the README/portfolio, and how to frame them |
> | **Audience** | Whoever captures the V1.0.0 release screenshots |
> | **Note** | This guide **describes** screenshots — it creates no image files. Paths below match the README placeholders. |

The README's **Screenshots** section already references eight images at fixed paths under
`docs/assets/`. This guide is the standard for capturing them so the placeholders resolve, plus an
optional extended set under `docs/assets/screenshots/` for a richer portfolio. Capture the **demo
state** from [DEMO_DATASET_GUIDE.md](./DEMO_DATASET_GUIDE.md) so every screen shows real, coherent
data (the same support-intent-classification story end to end).

---

## Global capture standards

| Setting | Standard | Why |
|---|---|---|
| **Theme** | **Dark mode** (the product is dark-first, Linear/Vercel/Cursor-inspired) | Matches the design language; looks best |
| **Resolution** | Capture at **2560×1440** (or 2× DPR on a 1280-wide viewport) | Crisp on retina + GitHub scaling |
| **Aspect** | Landscape ~16:9 for full pages; crop tight for detail panels | Consistent grid in the README table |
| **Format** | **PNG** (lossless; UI text stays sharp) | README links use `.png` |
| **Browser chrome** | **None** — capture the app viewport only, no address bar/tabs | Clean, product-focused frames |
| **Window width** | ≥ 1280 CSS px so the sidebar + content both show | Avoid the responsive collapsed nav |
| **Cursor** | Hidden, except where a hover/tooltip is the point | No stray pointers |
| **Redaction** | Use demo tenant data only; **no real secrets** — credential screens already mask | Secrets are write-only/masked by design; never expose one |
| **Light variant (optional)** | A light-mode copy of the **dashboard** + **advisor** as `*-light.png` | Shows theming maturity for portfolio |
| **Consistency** | Same org/project, same window size, same zoom across all shots | Looks like one product, one session |

> Keep filenames lowercase-kebab. The eight README paths are **fixed** — match them exactly. The
> extended portfolio set lives under `docs/assets/screenshots/` to avoid colliding with the README's
> top-level `docs/assets/*.png`.

---

## The eight README screenshots (capture these first — paths are fixed)

Recommended capture order follows the product story (each builds on the previous demo state).

| # | File (path the README expects) | Screen | What it must demonstrate | Framing tips |
|---|---|---|---|---|
| 1 | `docs/assets/dashboard.png` | **Project dashboard** | The operational + quality roll-up across agents, evaluations, benchmarks, trends, alerts | Full page. Ensure tiles show *non-zero* data (run the demo jobs first). The "hero" shot. |
| 2 | `docs/assets/agents.png` | **Agent registry** (list) | Framework-agnostic agents with search, filter, pagination | Have ≥ 3–4 agents with varied frameworks/health badges so the list looks alive |
| 3 | `docs/assets/agent-detail.png` | **Agent detail** | Overview / Versions / Health / Credentials / Settings tabs | Land on **Overview**; make the tab bar visible. Health badge = healthy. |
| 4 | `docs/assets/evaluations.png` | **Evaluation job** | Summary (pass-rate, cost, latency, tokens) + runs + per-run metric results | Open a **completed** job; expand one run's results so metric pass/fail chips show |
| 5 | `docs/assets/benchmarks.png` | **Benchmark leaderboard** | Compare agents / versions / prompts / models — ranked | Use the v1-prompt vs v2-prompt jobs so one clearly *wins*; show the ranking column |
| 6 | `docs/assets/advisor.png` | **AI Engineering Advisor** | Ranked recommendations with why / what changed / how to fix / expected improvement / confidence / severity | Open a recommendation card so the full fixed shape + a severity chip are visible |
| 7 | `docs/assets/debugger.png` | **AI Debugger** | Stage-by-stage execution timeline for one run | Pick a run where `PROMPT/MODEL/PARSER/OUTPUT` are populated and `MEMORY/RETRIEVER/TOOLS` show **NOT_INSTRUMENTED** — that honesty is the point |
| 8 | `docs/assets/knowledge.png` | **Knowledge graph** | Seeded failure-mode / recommendation catalogue + a node's neighbours | Open a node like `TIMEOUT` or `PROMPT_BLOAT` showing summary, remediation, and typed edges |

---

## Extended portfolio set (optional — `docs/assets/screenshots/`)

Capture these in addition for a deeper portfolio/case-study. Same global standards.

| File | Screen | What it demonstrates |
|---|---|---|
| `docs/assets/screenshots/agent-versions.png` | Agent → **Versions** tab | Immutable versions, active pointer, activate/rollback |
| `docs/assets/screenshots/agent-credentials.png` | Agent → **Credentials** tab | Masked credential (proves write-only/never-returned secret handling) |
| `docs/assets/screenshots/agent-health.png` | Agent → **Health** tab | Status, latency, rolling availability % (+ SSRF-guarded checks) |
| `docs/assets/screenshots/dataset-detail.png` | Dataset detail | Versions, items, per-version **stats**; immutability |
| `docs/assets/screenshots/dataset-import.png` | Dataset import dialog | CSV/JSON import producing a new immutable version |
| `docs/assets/screenshots/prompt-compare.png` | Prompt **Compare** | Two `{{variable}}` versions diffed side by side |
| `docs/assets/screenshots/evaluation-profile.png` | Evaluation profile editor | EXACT_MATCH + JSON_VALID + LATENCY rubric with thresholds |
| `docs/assets/screenshots/evaluation-runs.png` | Job → runs list | Fan-out: one run per dataset item, per-run status |
| `docs/assets/screenshots/regression.png` | Regression check | Baseline vs candidate, quality/latency/cost/token deltas |
| `docs/assets/screenshots/analytics.png` | Analytics | Cost / latency / token trends over a window |
| `docs/assets/screenshots/root-cause.png` | Root cause on a failed job | Findings: root cause, evidence, recommendation, confidence, severity |
| `docs/assets/screenshots/reports.png` | Reports export | JSON / CSV / HTML export + audit history |
| `docs/assets/screenshots/search.png` | Global search | One query across agents/datasets/prompts/jobs/benchmarks |
| `docs/assets/screenshots/swagger.png` | Swagger UI | The full documented REST surface (the "it's all real" shot) |
| `docs/assets/screenshots/login.png` | Login/Register | Polished auth UI (optional opener) |
| `docs/assets/screenshots/dashboard-light.png` | Dashboard, **light mode** | Theming maturity |
| `docs/assets/screenshots/advisor-light.png` | Advisor, **light mode** | Theming maturity |

---

## Recommended README narrative order

When arranging shots in the README table or a portfolio carousel, follow the platform story so a
reader "gets it" without reading prose:

1. **Dashboard** (the roll-up — set the stage)
2. **Agents** → **Agent detail** (the central aggregate)
3. **Evaluations** (the objective measurement — the centerpiece)
4. **Benchmarks** (is the new version better?)
5. **Advisor** (turn measurement into advice)
6. **AI Debugger** (explain a single run, honestly)
7. **Knowledge graph** (the catalogue findings link to)

This is the same arc as [DEMO_SCRIPT.md](./DEMO_SCRIPT.md), so the screenshots and the live demo
reinforce each other.

---

## Per-shot pre-capture checklist

- [ ] Demo data loaded (8 clean rows + 2 failing; ≥ 3 completed jobs; one clean + one worse job; ≥ 1
      failed run) per [DEMO_DATASET_GUIDE.md](./DEMO_DATASET_GUIDE.md).
- [ ] Dark mode on; window ≥ 1280 CSS px wide; browser chrome hidden.
- [ ] No real secrets on screen (credential screens already mask — verify).
- [ ] Tiles/lists/charts show **non-zero** data (nothing looks empty or "TODO").
- [ ] The feature's *signature detail* is visible: severity chips (advisor), `NOT_INSTRUMENTED` stages
      (debugger), a winning entry (benchmark), per-metric chips (evaluation), masked hint (credentials).
- [ ] Exported at 2× / 1440p, saved as PNG to the exact path above.

> Honesty reminder for captions: don't caption a screen with a capability that isn't shipped (e.g.
> async workers, provider-direct OpenAI/Anthropic clients, live distributed tracing). The
> `NOT_INSTRUMENTED` debugger stages and masked credentials are *features* worth showing, not flaws to
> hide.
