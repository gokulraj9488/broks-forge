"use client";

import { useQuery } from "@tanstack/react-query";
import { investigationApi } from "@/lib/api/investigation";

/**
 * One assembled investigation.
 *
 * A query rather than a mutation, unlike asking Brok: an investigation is a reading of the record as it
 * stands, and re-opening the same failure should give the same investigation. It is cached for a minute so
 * moving between the timeline, the graph and back does not re-assemble everything.
 */
export function useInvestigation(
  organizationId: string | undefined,
  evaluationId: string | undefined,
  projectId?: string,
) {
  return useQuery({
    queryKey: ["investigation", organizationId ?? "", evaluationId ?? "", projectId ?? ""],
    queryFn: () =>
      investigationApi.ofEvaluation(organizationId as string, evaluationId as string, { projectId }),
    enabled: !!organizationId && !!evaluationId,
    staleTime: 60_000,
  });
}
