# Broks Forge vs Weights & Biases

> Weights & Biases is the most established experiment-tracking platform in machine learning, with a
> deep feature set, a large user base and years of production maturity. Its Weave product extends it
> to LLM applications. This page compares scope and philosophy. Verify details against
> [W&B's documentation](https://docs.wandb.ai/).

## In one line

**Weights & Biases** tracks experiments and artifacts while you *train and tune models*.
**Broks Forge** records engineering decisions while you *build and operate AI systems* from models
you mostly did not train.

## What W&B does well

- **Experiment tracking** — metrics, hyperparameters, losses and system stats across thousands of
  runs, with best-in-class visualisation.
- **Artifact versioning and lineage** — datasets, models and checkpoints, with a real lineage graph.
  This is the closest thing in the comparison set to Broks Forge's Evolution.
- **Sweeps** — automated hyperparameter search.
- **Model registry** — staged promotion of trained models.
- **Reports** — shareable, narrative documents combining charts and commentary.
- **W&B Weave** — tracing and evaluation for LLM applications.
- **Maturity** — years of production use, extensive integrations, commercial support.

If you train or fine-tune models, W&B is the reference tool for that work and Broks Forge is not a
substitute for any part of it.

## Where the scope differs

### Different eras of AI work

**W&B was built for the training era.** The central question is *"which run produced the best
model?"* — you control the model, and the work is optimisation.

**Broks Forge is built for the composition era.** Most teams now call models they did not train.
The work is prompts, retrieval, tool wiring, provider choice, dataset curation and evaluation
policy. The central question is not *"which run won?"* but *"why is the system assembled this way,
and can we defend it?"*

### Lineage versus decisions

W&B's artifact lineage is genuinely good, and the closest overlap. The difference is what sits on
top of it:

| | W&B artifact lineage | Broks Forge |
| --- | --- | --- |
| Tracks versions | **Yes** | Yes |
| Shows what produced what | **Yes** | Yes |
| Records **why** a version was promoted | Notes/aliases, not modelled as reasoning | **Engineering Memory, verbatim** |
| Models a **Decision** as an object | No | **Yes** |
| Links **Evidence** to a decision | No | **Yes** |
| Flags decisions with **no** evidence | No | **Yes** |
| Surfaces **contradictions** in the record | No | **Yes** |
| Precedent search over failures | No | **Yes** |

W&B answers *what came from what*. Broks Forge additionally answers *why it was chosen, on what
evidence, and whether that still holds*.

### Reports versus derived briefs

W&B **Reports** are authored — a person assembles charts and writes the narrative. They are
excellent and completely manual.

Broks Forge **Engineering Briefs** are derived. Eight of them (Daily, Deployment, Incident, Prompt,
Evaluation, Dataset, Knowledge, Architecture), each written from the current record, following the
same narrative: what happened → why → evidence → impact → recommendation → next action. Nobody
assembles them, and they cannot go stale.

The trade is real: an authored report can say things a derived one cannot.

## Side by side

| | Weights & Biases | Broks Forge |
| --- | --- | --- |
| Primary use | Training, tuning, experiment tracking | Operating and evolving AI systems |
| Hyperparameter sweeps | **Yes** | No |
| Training metric visualisation | **Yes, excellent** | No |
| Model registry | **Yes** | Artifacts + AI Git |
| Artifact lineage | **Yes** | Yes (Forge Graph + Evolution) |
| LLM tracing | Yes (Weave) | No |
| Evaluation against datasets | Yes | **Yes, 14 metric types, pinned** |
| Decisions & evidence as objects | No | **Yes** |
| Engineering memory | No | **Yes** |
| Precedent search | No | **Yes** |
| Root-cause investigation | No | **Yes, four causal layers** |
| Grounded Q&A over the record | No | **Yes, deterministic** |
| Reports | Authored | Derived |
| Licensing | Commercial (free tier) | Open source, self-hosted |
| Maturity | **Very high** | Early |

## Philosophy

**W&B optimises the experiment.** More runs, better comparison, faster convergence. The unit of work
is a run, and value comes from the ability to compare many of them.

**Broks Forge optimises the decision.** Fewer, more consequential acts — a promotion, a rollback, a
dataset change — each recorded with its reasoning and its evidence. The unit of work is a decision,
and value comes from being able to explain and defend it later.

## Which to choose

**Choose W&B if** you train or fine-tune models, need sweeps and training visualisation, want a
mature model registry, or need authored reports for stakeholders.

**Choose Broks Forge if** you compose systems from models you did not train, and your hard problems
are prompt and dataset churn, undefendable promotions, lost reasoning and repeating failures.

**Run both if** you train models *and* operate systems built on them. W&B for the training lifecycle;
Broks Forge for the engineering record of the system in production.

See also: [Comparisons Overview](/docs/comparisons) ·
[The AI Engineering Operating System](/docs/ai-engineering-operating-system)
