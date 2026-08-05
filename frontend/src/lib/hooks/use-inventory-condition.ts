"use client";

import { useMemo } from "react";
import { useKnowledgeCatalog } from "@/lib/hooks/use-intelligence";
import type { KnowledgeObject, RegistryItem } from "@/lib/api/platform";
import { verdictOfJobStatus, worseOf, type VerdictState } from "@/lib/verdict";

/**
 * L-30 — every registry row must answer "how is this doing?", not merely "what is this?".
 *
 * The condition of an artifact is derived client-side by joining the knowledge catalog (which the Registry
 * already loads for its Knowledge scope) onto the artifact list. No new endpoint, no new persistence: the
 * evidence, decisions and knowledge that the platform already derives are simply attributed back to the
 * artifacts they concern.
 */
export interface ArtifactCondition {
  state: VerdictState;
  /** One short clause for the row, e.g. "3 evaluations, 1 failing". */
  summary: string;
  evidenceCount: number;
  failingCount: number;
  decisionCount: number;
  knowledgeCount: number;
}

const EMPTY: ArtifactCondition = {
  state: "unknown",
  summary: "No evidence yet",
  evidenceCount: 0,
  failingCount: 0,
  decisionCount: 0,
  knowledgeCount: 0,
};

/**
 * Builds a condition index keyed by "<artifactType>:<entityId>" for a whole organization in one pass, so a
 * 100-row registry costs one shared query rather than 100.
 */
export function useInventoryCondition(organizationId: string | undefined) {
  const { data } = useKnowledgeCatalog(organizationId, { size: 100, sort: "recent" });

  return useMemo(() => {
    const index = new Map<string, ArtifactCondition>();
    for (const o of data?.content ?? []) {
      if (!o.artifactEntityId) continue;
      const key = `${o.artifactType}:${o.artifactEntityId}`;
      const cur = index.get(key) ?? { ...EMPTY, state: "healthy" as VerdictState, summary: "" };
      const next: ArtifactCondition = { ...cur };

      if (o.type === "evidence" || o.type === "observation") {
        next.evidenceCount = cur.evidenceCount + (o.type === "evidence" ? 1 : 0);
        if (o.outcome === "FAILED" || o.outcome === "CANCELLED") next.failingCount = cur.failingCount + 1;
        next.state = worseOf(next.state, verdictOfJobStatus(o.outcome));
      } else if (o.type === "decision") {
        next.decisionCount = cur.decisionCount + 1;
      } else if (o.type === "knowledge") {
        next.knowledgeCount = cur.knowledgeCount + 1;
      }
      index.set(key, next);
    }

    // Compose each row's one-clause summary once the counts are final.
    for (const [key, c] of index) {
      index.set(key, { ...c, state: conditionState(c), summary: conditionSummary(c) });
    }
    return index;
  }, [data]);
}

/** Looks a registry item's condition up in the index, defaulting honestly to "not yet known" (L-34). */
export function conditionOf(
  index: Map<string, ArtifactCondition>,
  item: RegistryItem,
): ArtifactCondition {
  if (!item.entityId) return EMPTY;
  return index.get(`${item.type}:${item.entityId}`) ?? EMPTY;
}

function conditionState(c: ArtifactCondition): VerdictState {
  if (c.failingCount > 0) return "failed";
  if (c.evidenceCount === 0 && c.knowledgeCount === 0 && c.decisionCount === 0) return "unknown";
  if (c.evidenceCount === 0) return "attention";
  return "healthy";
}

function conditionSummary(c: ArtifactCondition): string {
  const parts: string[] = [];
  if (c.evidenceCount > 0) {
    parts.push(
      c.failingCount > 0
        ? `${c.evidenceCount} evaluation${c.evidenceCount === 1 ? "" : "s"}, ${c.failingCount} failing`
        : `${c.evidenceCount} evaluation${c.evidenceCount === 1 ? "" : "s"} passing`,
    );
  }
  if (c.decisionCount > 0) parts.push(`${c.decisionCount} decision${c.decisionCount === 1 ? "" : "s"}`);
  if (c.knowledgeCount > 0) parts.push(`${c.knowledgeCount} knowledge`);
  return parts.length > 0 ? parts.join(" · ") : "No evidence yet";
}

/** Knowledge objects that concern one artifact — powers the registry's hover preview. */
export function knowledgeFor(
  objects: KnowledgeObject[],
  item: RegistryItem,
): KnowledgeObject[] {
  if (!item.entityId) return [];
  return objects.filter((o) => o.artifactType === item.type && o.artifactEntityId === item.entityId);
}
