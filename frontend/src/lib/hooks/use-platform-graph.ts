"use client";

import { useQuery } from "@tanstack/react-query";
import { platformApi } from "@/lib/api/platform";

const keys = {
  graph: (o: string, knowledge: boolean) =>
    ["organizations", o, "platform", "graph", knowledge ? "knowledge" : "base"] as const,
};

/**
 * Reads the organization's engineering graph. Pass {@code includeKnowledge} to overlay the derived
 * engineering-knowledge nodes (P11) on the same graph. Does not retry: when the platform is disabled the
 * endpoint is absent (404), and the consuming view falls back to the knowledge library rather than erroring.
 */
export function usePlatformGraph(
  organizationId: string | undefined,
  options: { includeKnowledge?: boolean } = {},
) {
  const includeKnowledge = options.includeKnowledge ?? false;
  return useQuery({
    queryKey: keys.graph(organizationId ?? "", includeKnowledge),
    queryFn: () => platformApi.graph(organizationId as string, { includeKnowledge }),
    enabled: !!organizationId,
    retry: false,
  });
}
