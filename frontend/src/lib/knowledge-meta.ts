import { substrateMeta, type SubstrateMeta } from "@/lib/substrate";
import type { LucideIcon } from "lucide-react";

/**
 * How each engineering-knowledge kind looks and reads.
 *
 * This is a thin projection of {@link substrateMeta} — structural identity has exactly one home
 * (lib/substrate.ts) so an Observation looks identical in the registry catalog, the Intelligence tab, the
 * knowledge detail page and the graph overlay. Keeping the older shape here means every existing consumer
 * continues to work unchanged while the colours come from the one grammar.
 */
export interface KnowledgeKindMeta {
  icon: LucideIcon;
  color: string;
  ring: string;
  label: string;
  /** One line: what this kind is, in engineering terms. */
  blurb: string;
}

export const KNOWLEDGE_KINDS = ["observation", "claim", "decision", "evidence", "knowledge"] as const;

export function knowledgeKindMeta(type: string): KnowledgeKindMeta {
  const m: SubstrateMeta = substrateMeta(type);
  return { icon: m.icon, color: m.color, ring: m.accent, label: m.label, blurb: m.blurb };
}

/** The knowledge kind encoded at the front of a composite id, e.g. "decision:prompt-version:…" → "decision". */
export function knowledgeKindOf(id: string): string | null {
  const prefix = id.split(":", 1)[0];
  return (KNOWLEDGE_KINDS as readonly string[]).includes(prefix) ? prefix : null;
}
