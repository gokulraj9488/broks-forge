"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { platformApi, type KnowledgeQuery, type KnowledgeType } from "@/lib/api/platform";
import { knowledgeKindOf } from "@/lib/knowledge-meta";

const keys = {
  intelligence: (o: string, t: string, id: string) =>
    ["organizations", o, "platform", "intelligence", t, id] as const,
  revisions: (o: string, t: string, id: string) =>
    ["organizations", o, "platform", "revisions", t, id] as const,
  compare: (o: string, t: string, id: string, b: string, tg: string) =>
    ["organizations", o, "platform", "compare", t, id, b, tg] as const,
  knowledge: (o: string, q: KnowledgeQuery) => ["organizations", o, "platform", "knowledge", q] as const,
  object: (o: string, id: string) => ["organizations", o, "platform", "knowledge-object", id] as const,
};

/**
 * Reads the engineering intelligence of one artifact (observations, claims, decisions, evidence, knowledge,
 * memory). Does not retry: when the platform is disabled the endpoint is absent (404) and the consuming tab
 * shows a graceful empty state.
 */
export function useArtifactIntelligence(
  organizationId: string | undefined,
  type: string | undefined,
  entityId: string | undefined,
) {
  return useQuery({
    queryKey: keys.intelligence(organizationId ?? "", type ?? "", entityId ?? ""),
    queryFn: () => platformApi.intelligence(organizationId as string, type as string, entityId as string),
    enabled: !!organizationId && !!type && !!entityId,
    retry: false,
  });
}

/** Reads an artifact's engineering revision timeline (AI Git). */
export function useArtifactRevisions(
  organizationId: string | undefined,
  type: string | undefined,
  entityId: string | undefined,
) {
  return useQuery({
    queryKey: keys.revisions(organizationId ?? "", type ?? "", entityId ?? ""),
    queryFn: () => platformApi.revisions(organizationId as string, type as string, entityId as string),
    enabled: !!organizationId && !!type && !!entityId,
    retry: false,
  });
}

/** Compares two engineering revisions of the same artifact. Enabled only once both revisions are chosen. */
export function useRevisionComparison(
  organizationId: string | undefined,
  type: string | undefined,
  entityId: string | undefined,
  baseId: string | undefined,
  targetId: string | undefined,
) {
  return useQuery({
    queryKey: keys.compare(organizationId ?? "", type ?? "", entityId ?? "", baseId ?? "", targetId ?? ""),
    queryFn: () =>
      platformApi.compare(
        organizationId as string,
        type as string,
        entityId as string,
        baseId as string,
        targetId as string,
      ),
    enabled: !!organizationId && !!type && !!entityId && !!baseId && !!targetId && baseId !== targetId,
    retry: false,
  });
}

/**
 * Reads one engineering-knowledge object by its composite id. The knowledge kind is decoded from the id prefix
 * and routed to the matching typed endpoint (decision / claim / observation / evidence / knowledge).
 */
export function useKnowledgeObject(organizationId: string | undefined, id: string | undefined) {
  const kind = id ? (knowledgeKindOf(id) as KnowledgeType | null) : null;
  return useQuery({
    queryKey: keys.object(organizationId ?? "", id ?? ""),
    queryFn: () => platformApi.knowledgeObject(organizationId as string, kind as KnowledgeType, id as string),
    enabled: !!organizationId && !!id && !!kind,
    retry: false,
  });
}

/** Server-backed knowledge catalog listing. Keeps the previous page visible while the next loads. */
export function useKnowledgeCatalog(organizationId: string | undefined, query: KnowledgeQuery) {
  return useQuery({
    queryKey: keys.knowledge(organizationId ?? "", query),
    queryFn: () => platformApi.knowledge(organizationId as string, query),
    enabled: !!organizationId,
    placeholderData: keepPreviousData,
    retry: false,
  });
}
