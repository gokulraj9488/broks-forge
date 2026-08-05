import {
  Activity,
  Bot,
  Building2,
  Cpu,
  Database,
  FileText,
  FlaskConical,
  FolderKanban,
  Gavel,
  Lightbulb,
  type LucideIcon,
  Network,
  Plug,
  Quote,
  ShieldCheck,
  Telescope,
  Trophy,
} from "lucide-react";

/**
 * Structural identity — the single source of truth for "what this thing is" (Volume III §11.1, L-20, L-83).
 *
 * THE RULE, stated once so it can never drift again:
 *
 *   Cool, low-chroma hues answer  "what is this?"      (structural identity — this file)
 *   Warm/saturated hues answer    "how is it doing?"   (engineering verdict — lib/verdict.ts)
 *
 * The two palettes are disjoint by construction: no colour in this file appears in the verdict palette, and
 * verdict colours are never used to identify an object type. An engineer must never confuse a provider being
 * *a provider* with a provider being *healthy*.
 *
 * Object types come in two families. Both are structural, but the REASONING family (the derived knowledge
 * objects) additionally carries a shared soft/dashed treatment so the family reads before the hue does —
 * you can see that something is reasoning layered over engineering without reading a word.
 *
 * Every surface in the product resolves its icon and colour here. Previously six components each kept their
 * own copy of this map, which is how `claim` drifted to amber and `evidence` to emerald — both verdict
 * colours. One map, one grammar.
 */
export type SubstrateFamily = "substrate" | "reasoning";

export interface SubstrateMeta {
  icon: LucideIcon;
  /** Text colour class — cool and low-chroma, never a verdict hue. */
  color: string;
  /** Border/accent class for cards, node borders and identity chips. */
  accent: string;
  /** Raw hex, for canvas/SVG export where Tailwind classes cannot reach. */
  hex: string;
  label: string;
  family: SubstrateFamily;
  /** Vertical tier in the Forge Graph — organization at the top, reasoning layered beneath. */
  tier: number;
  /** One line of teaching copy (L-11), used by knowledge surfaces. */
  blurb: string;
}

const SUBSTRATE: Record<string, SubstrateMeta> = {
  organization: {
    icon: Building2, color: "text-violet-300", accent: "border-violet-400/40", hex: "#c4b5fd",
    label: "Organization", family: "substrate", tier: 0,
    blurb: "The boundary your engineering record lives in.",
  },
  project: {
    icon: FolderKanban, color: "text-sky-300", accent: "border-sky-400/40", hex: "#7dd3fc",
    label: "Project", family: "substrate", tier: 1,
    blurb: "Where engineering happens — the artifacts and the evaluations that judge them.",
  },
  provider: {
    icon: Plug, color: "text-cyan-300", accent: "border-cyan-400/40", hex: "#67e8f9",
    label: "Provider", family: "substrate", tier: 2,
    blurb: "An LLM provider your agents call.",
  },
  model: {
    icon: Cpu, color: "text-teal-300", accent: "border-teal-400/40", hex: "#5eead4",
    label: "Model", family: "substrate", tier: 3,
    blurb: "A specific model served by a provider.",
  },
  agent: {
    icon: Bot, color: "text-indigo-300", accent: "border-indigo-400/40", hex: "#a5b4fc",
    label: "Agent", family: "substrate", tier: 4,
    blurb: "An AI system you have registered and can evaluate.",
  },
  prompt: {
    icon: FileText, color: "text-fuchsia-300", accent: "border-fuchsia-400/40", hex: "#f0abfc",
    label: "Prompt", family: "substrate", tier: 5,
    blurb: "Versioned instruction text — the story of intent.",
  },
  dataset: {
    icon: Database, color: "text-blue-300", accent: "border-blue-400/40", hex: "#93c5fd",
    label: "Dataset", family: "substrate", tier: 6,
    blurb: "The ground truth an evaluation measures against.",
  },
  evaluation: {
    icon: FlaskConical, color: "text-purple-300", accent: "border-purple-400/40", hex: "#d8b4fe",
    label: "Evaluation", family: "substrate", tier: 7,
    blurb: "A run that produced evidence about an artifact.",
  },
  run: {
    icon: Activity, color: "text-teal-300", accent: "border-teal-400/40", hex: "#5eead4",
    label: "Run", family: "substrate", tier: 7,
    blurb: "One recorded execution inside an evaluation.",
  },
  benchmark: {
    icon: Trophy, color: "text-sky-300", accent: "border-sky-400/40", hex: "#7dd3fc",
    label: "Benchmark", family: "substrate", tier: 7,
    blurb: "A shared standard your artifacts are measured against.",
  },

  // ---- Reasoning family: same cool band, plus the dashed/soft treatment applied by consumers ----
  observation: {
    icon: Telescope, color: "text-sky-300", accent: "border-sky-400/40", hex: "#7dd3fc",
    label: "Observation", family: "reasoning", tier: 8,
    blurb: "A measured fact — what an evaluation actually recorded.",
  },
  evidence: {
    icon: ShieldCheck, color: "text-cyan-300", accent: "border-cyan-400/40", hex: "#67e8f9",
    label: "Evidence", family: "reasoning", tier: 9,
    blurb: "The evaluations that support a claim or decision.",
  },
  claim: {
    icon: Quote, color: "text-indigo-300", accent: "border-indigo-400/40", hex: "#a5b4fc",
    label: "Claim", family: "reasoning", tier: 10,
    blurb: "An assertion about an artifact, backed by evidence.",
  },
  decision: {
    icon: Gavel, color: "text-violet-300", accent: "border-violet-400/40", hex: "#c4b5fd",
    label: "Decision", family: "reasoning", tier: 11,
    blurb: "An engineering choice — a promotion or deprecation — and why it was made.",
  },
  knowledge: {
    icon: Lightbulb, color: "text-fuchsia-300", accent: "border-fuchsia-400/40", hex: "#f0abfc",
    label: "Knowledge", family: "reasoning", tier: 12,
    blurb: "Durable engineering knowledge that emerged from decisions and evidence.",
  },
};

const DEFAULT: SubstrateMeta = {
  icon: Network,
  color: "text-muted-foreground",
  accent: "border-border",
  hex: "#a1a1aa",
  label: "Object",
  family: "substrate",
  tier: 13,
  blurb: "An engineering object.",
};

/** Resolves any object type — artifact or reasoning — to its structural identity. */
export function substrateMeta(type: string): SubstrateMeta {
  return SUBSTRATE[type] ?? DEFAULT;
}

/** True when a type belongs to the derived reasoning layer (rendered with the soft/dashed treatment). */
export function isReasoning(type: string): boolean {
  return substrateMeta(type).family === "reasoning";
}

/** The reasoning family's shared treatment — the thing that makes "this is thinking" legible pre-hue. */
export const REASONING_TREATMENT = "border-dashed bg-muted/30";
