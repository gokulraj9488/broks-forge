"use client";

import { useQuery } from "@tanstack/react-query";
import { platformApi } from "@/lib/api/platform";

const keys = {
  evolution: (o: string, t: string, id: string) =>
    ["organizations", o, "platform", "evolution", t, id] as const,
};

/**
 * Reads the engineering evolution of one artifact. Does not retry: when the platform is disabled the endpoint
 * is absent (404) and the consuming tab shows a graceful empty state instead of erroring.
 */
export function useArtifactEvolution(
  organizationId: string | undefined,
  type: string | undefined,
  entityId: string | undefined,
) {
  return useQuery({
    queryKey: keys.evolution(organizationId ?? "", type ?? "", entityId ?? ""),
    queryFn: () => platformApi.evolution(organizationId as string, type as string, entityId as string),
    enabled: !!organizationId && !!type && !!entityId,
    retry: false,
  });
}
