"use client";

import { useQuery } from "@tanstack/react-query";
import { platformApi } from "@/lib/api/platform";

const keys = {
  health: (o: string) => ["organizations", o, "platform", "health"] as const,
};

/**
 * Reads the organization's platform integrity snapshot. Does not retry: when the platform is disabled the
 * endpoint is absent (404), and the consuming panel simply stays hidden rather than surfacing an error.
 */
export function usePlatformHealth(organizationId: string | undefined) {
  return useQuery({
    queryKey: keys.health(organizationId ?? ""),
    queryFn: () => platformApi.health(organizationId as string),
    enabled: !!organizationId,
    retry: false,
  });
}
