/**
 * The verdict system — Volume III, P-1 (meaning before measurement) and P-2 (nothing asserted that cannot be
 * traced).
 *
 * Every object of consequence in Broks Forge must be able to state itself in one honest sentence, carrying
 * (a) a verdict state, (b) an epistemic status, and (c) a route to its receipts. This module is the single
 * source of truth for those three things, so a Decision reads the same way in the Brief, the Registry, a
 * workspace and the graph.
 *
 * Colour discipline (L-20, L-83): verdict colours are the most saturated in the product and express STATE
 * only. Object-identity hues live with the object types and are never used here.
 */

// ---------------------------------------------------------------------------
// Verdict state — the only evaluative vocabulary in the product
// ---------------------------------------------------------------------------
export type VerdictState = "healthy" | "attention" | "risk" | "failed" | "unknown";

export interface VerdictStyle {
  /** Saturated accent — borders, dots, icon colour. Never a large fill. */
  fg: string;
  /** Very low-alpha tint for banners. */
  bg: string;
  border: string;
  /** The word that always accompanies the colour (L-20: colour is never the sole carrier). */
  word: string;
}

export const VERDICT_STYLE: Record<VerdictState, VerdictStyle> = {
  healthy: { fg: "text-emerald-400", bg: "bg-emerald-500/5", border: "border-emerald-500/30", word: "Healthy" },
  attention: { fg: "text-amber-400", bg: "bg-amber-500/5", border: "border-amber-500/30", word: "Needs attention" },
  risk: { fg: "text-orange-400", bg: "bg-orange-500/5", border: "border-orange-500/30", word: "At risk" },
  failed: { fg: "text-rose-400", bg: "bg-rose-500/5", border: "border-rose-500/30", word: "Failing" },
  // L-34/L-56: absence is not health. Unknown is deliberately distinct from both healthy and plain chrome.
  unknown: { fg: "text-zinc-400", bg: "bg-zinc-500/5", border: "border-zinc-500/30", word: "Not yet known" },
};

/** Rank for sorting — worse first. Unknown sits between healthy and attention: it is not good news. */
const SEVERITY: Record<VerdictState, number> = {
  failed: 4,
  risk: 3,
  attention: 2,
  unknown: 1,
  healthy: 0,
};

export function worseOf(a: VerdictState, b: VerdictState): VerdictState {
  return SEVERITY[a] >= SEVERITY[b] ? a : b;
}

export function verdictSeverity(state: VerdictState): number {
  return SEVERITY[state];
}

/** Maps an evaluation-job status onto the product's single verdict vocabulary. */
export function verdictOfJobStatus(status: string | null | undefined): VerdictState {
  switch (status) {
    case "COMPLETED":
      return "healthy";
    case "FAILED":
      return "failed";
    case "CANCELLED":
      return "attention";
    case "RUNNING":
    case "PENDING":
      return "unknown";
    default:
      return "unknown";
  }
}

// ---------------------------------------------------------------------------
// Epistemic status — L-33. Every statement carries exactly one.
// ---------------------------------------------------------------------------
export type EpistemicStatus = "derived" | "inferred" | "suggested" | "unknown";

export interface EpistemicStyle {
  label: string;
  /** How the product speaks at this status (L-23, §28.1). */
  register: string;
  fg: string;
  border: string;
}

/**
 * Epistemic status is a THIRD dimension, orthogonal to both structural identity and verdict — so it must not
 * compete for hue with either. It is therefore rendered in neutral chrome and distinguished entirely by its
 * icon and its word, which is also the most honest encoding: "how sure are we" is a statement, not a state.
 *
 * This keeps exactly two coloured languages in the product (identity = cool, verdict = warm) instead of three
 * competing ones.
 */
export const EPISTEMIC_STYLE: Record<EpistemicStatus, EpistemicStyle> = {
  derived: {
    label: "Derived",
    register: "Traceable to real engineering records.",
    fg: "text-muted-foreground",
    border: "border-border",
  },
  inferred: {
    label: "Inferred",
    register: "A causal reading of the evidence — it could be wrong.",
    fg: "text-foreground/70",
    border: "border-foreground/25",
  },
  suggested: {
    label: "Suggested",
    register: "A proposal about what to do next, not a fact.",
    fg: "text-foreground/70",
    border: "border-dashed border-foreground/30",
  },
  unknown: {
    label: "Not known",
    register: "There is no evidence for this yet.",
    fg: "text-muted-foreground",
    border: "border-dashed border-border",
  },
};

/** L-57: confidence is a three-step verbal ladder, never a fabricated percentage. */
export type Confidence = "consistent-with" | "likely" | "near-certain";

export const CONFIDENCE_WORD: Record<Confidence, string> = {
  "consistent-with": "consistent with",
  likely: "likely",
  "near-certain": "near-certain",
};

/**
 * Derives a verbal confidence from how much real evidence stands behind a statement. Deliberately coarse —
 * three steps is all the honesty the underlying evidence supports.
 */
export function confidenceFromEvidence(count: number): Confidence {
  if (count >= 8) return "near-certain";
  if (count >= 3) return "likely";
  return "consistent-with";
}

// ---------------------------------------------------------------------------
// The verdict itself
// ---------------------------------------------------------------------------
export interface Provenance {
  /** Plain-English statement of what this was derived from, e.g. "12 evaluations since v3". */
  basis: string;
  /** Optional route to the receipts (D3). */
  href?: string | null;
}

export interface Verdict {
  state: VerdictState;
  /** The sentence. Declarative, specific, never cheerful (L-6, L-23). */
  headline: string;
  /** Optional second clause: the consequence — why the reader should care. */
  consequence?: string | null;
  status: EpistemicStatus;
  provenance?: Provenance | null;
}

/** Pluralization helper so generated sentences never read like machine output. */
export function plural(count: number, one: string, many?: string): string {
  return `${count} ${count === 1 ? one : (many ?? one + "s")}`;
}
