/**
 * Plain-English explanations for every Engineering Intelligence capability, surfaced through the "i" info
 * buttons across the platform. The goal: a first-time visitor understands, in a sentence or two per point, what
 * a feature is and why it makes Broks Forge an AI *engineering* platform rather than another observability tool.
 *
 * Deliberately value-focused — no implementation detail, no API names.
 */
export interface InfoContent {
  title: string;
  tagline: string;
  what: string;
  why: string;
  vsObservability: string;
  howToUse: string;
}

export type InfoFeature =
  | "brok"
  | "root-cause"
  | "engineering-intelligence"
  | "knowledge"
  | "forge-graph"
  | "registry"
  | "ai-git"
  | "evolution"
  | "reasoning-overlay"
  | "observation"
  | "claim"
  | "decision"
  | "evidence"
  | "memory"
  | "revision";

export const INTELLIGENCE_INFO: Record<InfoFeature, InfoContent> = {
  brok: {
    title: "Brok",
    tagline: "Your engineering partner — not a chatbot.",
    what: "Ask about your AI system in plain English and get an answer read from your own engineering record: evaluations and their runs, promotions, evidence, engineering memory and the relationships between artifacts. Ask \"has this happened before?\" and Brok searches the record for precedents — what broke last time, and what the team did about it. It also writes the eight engineering briefs and can open an investigation.",
    why: "Every statement declares how it is known — derived, inferred, suggested, or simply not known — and every recommendation carries its evidence, its confidence, its impact and a next action. Nothing is generated from nothing, so an answer can always be checked.",
    vsObservability:
      "A chatbot bolted onto a dashboard improvises from documentation and sounds equally confident either way. Brok reasons over your engineering record, refuses what that record cannot support, and hands you back into the workflow the answer came from.",
    howToUse:
      "Press Ctrl+. from anywhere, ask a question, or open a brief. Follow-ups inherit the subject, so \"show me the evidence\" and \"should I promote it?\" just work. Click any referenced record — or a node in the graph beside it — to move the conversation onto that artifact.",
  },
  "root-cause": {
    title: "Root Cause Explorer",
    tagline: "An investigation, not an error message.",
    what: "Opening a failure assembles the whole investigation in one place: the chronology that led to it, the cause at four depths — what broke, what made it likely, what the record has already lived through, and what changed just before — plus the evidence, the AI Git revisions, the decisions, the engineering memory and every earlier failure on the same ground.",
    why: "The first answer is rarely the last one. Separating the immediate cause from its contributing, historical and change-related causes is the difference between restarting a job and understanding why it keeps failing.",
    vsObservability:
      "A log explorer hands you a search box and a haystack. This hands you the investigation already assembled — dated, cited, and honest about which parts are inferred rather than derived.",
    howToUse:
      "Open Investigate from any evaluation, or ask Brok to investigate one. Click a timeline event or a referenced record to move the graph with it, then continue in Brok without losing the investigation.",
  },
  "engineering-intelligence": {
    title: "Engineering Intelligence",
    tagline: "The reasoning layer of your AI systems.",
    what: "A living, traceable record of what your AI engineering produced: what was observed, what was decided, what evidence supports it, and what durable knowledge emerged.",
    why: "Engineering decisions usually live in people's heads, chat threads and commit messages. Here they are first-class objects, derived automatically from your real work — never invented.",
    vsObservability:
      "Observability tools show you traces and metrics of what happened. Engineering Intelligence explains why it happened and what you now know because of it.",
    howToUse:
      "Open any artifact's Intelligence tab to see its observations, decisions, evidence and knowledge, then follow the links to explore how they connect.",
  },
  knowledge: {
    title: "Knowledge",
    tagline: "What your engineering has proven — not just logged.",
    what: "A durable insight about an artifact that emerged from real decisions and evidence, e.g. \"this prompt's canonical revision is v3, backed by 4 evaluations.\"",
    why: "Knowledge is never fabricated. It only exists where a genuine decision and supporting evidence exist, so it always traces back to something real.",
    vsObservability:
      "A dashboard forgets everything the moment you close it. Knowledge persists as an engineering fact you can navigate, cite and build on.",
    howToUse:
      "Open a Knowledge object to see what created it, the decision and evidence behind it, and every artifact it affects.",
  },
  "forge-graph": {
    title: "Forge Graph",
    tagline: "Your AI organization as a connected system.",
    what: "A live map of every engineering artifact — providers, models, agents, prompts, datasets, evaluations — and the real relationships between them.",
    why: "AI systems are graphs, not tables. Seeing the connections is how you understand impact, lineage and reuse at a glance.",
    vsObservability:
      "Most tools give you isolated lists. The Forge Graph shows the whole system and, with reasoning turned on, layers engineering knowledge on top of it.",
    howToUse:
      "Click any node to focus its neighbours. Toggle \"Show reasoning\" to overlay observations, decisions and knowledge onto the artifacts they came from.",
  },
  registry: {
    title: "Registry",
    tagline: "One catalog of everything you engineer.",
    what: "A single, searchable catalog of every engineering artifact and every piece of engineering knowledge in your organization.",
    why: "Discovery should be one place, not scattered across modules. The Registry unifies artifacts and the knowledge derived from them.",
    vsObservability:
      "Observability catalogs runs and traces. This catalogs your engineering — artifacts and the reasoning about them — side by side.",
    howToUse:
      "Switch between the Artifacts and Knowledge scopes to browse either, then open any item to dive into its engineering workspace.",
  },
  "ai-git": {
    title: "AI Git",
    tagline: "Version control for engineering reasoning.",
    what: "A revision timeline for an artifact: every version, which was promoted, what it superseded, whether it can be rolled back, and the rationale behind each change.",
    why: "This is not source control. It answers engineering questions — what changed, why, what it replaced and what evidence supported it.",
    vsObservability:
      "Observability tools rarely explain change. AI Git treats each revision as an engineering decision with a rationale you can compare and trace.",
    howToUse:
      "Open the Revisions view to walk the timeline, then compare any two revisions field-by-field to see exactly what changed.",
  },
  evolution: {
    title: "Engineering Evolution",
    tagline: "Where an artifact came from and what it affects.",
    what: "The lineage of an artifact — what it depends on, what depends on it, its transitive impact, its historical revisions and the evidence about it.",
    why: "Before you change something, you should know what it will affect. Evolution makes lineage and impact explicit.",
    vsObservability:
      "Traces show a single request's path. Evolution shows an artifact's place in the whole engineering system over time.",
    howToUse:
      "Open an artifact's Evolution tab to see its dependencies, dependents, impact and history — all derived from the live model.",
  },
  "reasoning-overlay": {
    title: "Reasoning overlay",
    tagline: "See the thinking on top of the system.",
    what: "An overlay that adds engineering-knowledge nodes — observations, decisions, evidence, knowledge — onto the Forge Graph, connected to the artifacts they came from.",
    why: "It makes the invisible visible: you can literally see reasoning layered over your artifacts rather than buried in pages.",
    vsObservability:
      "No observability tool shows reasoning as part of the system graph. This does.",
    howToUse:
      "Toggle \"Show reasoning\" on the graph, then select a knowledge node to open its dedicated page.",
  },
  observation: {
    title: "Observation",
    tagline: "A measured fact.",
    what: "Something your platform actually measured — the outcome of an evaluation against an artifact.",
    why: "Observations are the raw, factual ground truth that claims, decisions and knowledge are built from.",
    vsObservability:
      "A metric point is a number. An observation is a fact tied to the artifact it measured and the reasoning it supports.",
    howToUse: "Follow an observation's links to the evaluation it came from and the artifact it measured.",
  },
  claim: {
    title: "Claim",
    tagline: "An assertion backed by evidence.",
    what: "A statement about an artifact — such as which revision is canonical — supported by observations and a decision.",
    why: "Claims separate what is asserted from what is merely logged, and always cite their support.",
    vsObservability:
      "Dashboards state numbers; claims state conclusions and show the evidence behind them.",
    howToUse: "Open a claim to see the decision it is based on and the evidence that supports it.",
  },
  decision: {
    title: "Decision",
    tagline: "An engineering choice, with a reason.",
    what: "A real engineering action — promoting a revision to active, or deprecating an artifact — captured with its rationale and what it superseded.",
    why: "Decisions are usually lost to history. Here each one is a durable, traceable object that answers \"why did this change?\".",
    vsObservability:
      "Observability records events. A Decision records intent — the choice a human or process made, and why.",
    howToUse: "Open a decision to see its rationale, what it superseded, and the evidence that informed it.",
  },
  evidence: {
    title: "Evidence",
    tagline: "What supports a conclusion.",
    what: "An evaluation framed as support for a claim or decision about an artifact.",
    why: "Every conclusion in the platform can show its receipts. Evidence is those receipts.",
    vsObservability:
      "A run is just a run. Evidence connects that run to the claim or decision it justifies.",
    howToUse: "Open a piece of evidence to see what it supports and open the underlying evaluation.",
  },
  memory: {
    title: "Engineering Memory",
    tagline: "Why things are the way they are.",
    what: "The answers to \"why was this changed?\" — derived from the real decisions behind an artifact.",
    why: "New teammates inherit the reasoning, not just the result. Memory turns past decisions into institutional knowledge.",
    vsObservability:
      "Version history tells you what changed. Memory tells you why, in engineering terms.",
    howToUse: "Read an artifact's memory on its Intelligence tab to understand the reasoning behind its current state.",
  },
  revision: {
    title: "Revision",
    tagline: "One engineering version.",
    what: "A single immutable version of an artifact, with its snapshot, rationale, promotion status and rollback readiness.",
    why: "Revisions make change reviewable: you can compare any two and see exactly what moved.",
    vsObservability:
      "Prompt history shows text diffs. A revision is a full engineering snapshot you can reason over.",
    howToUse: "Compare two revisions in the AI Git view to see a field-by-field diff and the rationale for the change.",
  },
};
