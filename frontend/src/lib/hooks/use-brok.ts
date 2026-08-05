"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { brokApi, type BrokAskRequest } from "@/lib/api/brok";

const keys = {
  suggestions: (o: string, p: string, f: string) => ["brok", o, "suggestions", p, f] as const,
  context: (o: string, p: string, f: string) => ["brok", o, "context", p, f] as const,
  briefs: (o: string, p: string) => ["brok", o, "briefs", p] as const,
};

/**
 * Asking is a mutation rather than a query even though it changes nothing on the server: each ask is an
 * event in the engineer's session, and answers accumulate into a thread the workspace owns. Caching them by
 * question would be wrong — the same question asked after a deployment must be re-derived.
 */
export function useAskBrok(organizationId: string | undefined) {
  return useMutation({
    mutationFn: (request: BrokAskRequest) => brokApi.ask(organizationId as string, request),
  });
}

export function useBrokSuggestions(
  organizationId: string | undefined,
  projectId?: string,
  focus?: string,
) {
  return useQuery({
    queryKey: keys.suggestions(organizationId ?? "", projectId ?? "", focus ?? ""),
    queryFn: () => brokApi.suggestions(organizationId as string, { projectId, focus }),
    enabled: !!organizationId,
  });
}

export function useBrokContext(
  organizationId: string | undefined,
  projectId?: string,
  focus?: string,
) {
  return useQuery({
    queryKey: keys.context(organizationId ?? "", projectId ?? "", focus ?? ""),
    queryFn: () => brokApi.context(organizationId as string, { projectId, focus }),
    enabled: !!organizationId,
  });
}

export function useBrokBriefs(organizationId: string | undefined, projectId?: string) {
  return useQuery({
    queryKey: keys.briefs(organizationId ?? "", projectId ?? ""),
    queryFn: () => brokApi.briefs(organizationId as string, { projectId }),
    enabled: !!organizationId,
  });
}

/**
 * Requests one Engineering Brief. Like asking, this is a mutation: a brief is written against the record as
 * it stands at the moment it is opened, and it joins the same thread an answer does.
 */
export function useRequestBrief(organizationId: string | undefined) {
  return useMutation({
    mutationFn: ({ kind, projectId }: { kind: string; projectId?: string }) =>
      brokApi.brief(organizationId as string, kind, { projectId }),
  });
}
