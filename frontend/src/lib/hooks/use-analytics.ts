"use client";

import { useQuery } from "@tanstack/react-query";
import { analyticsApi } from "@/lib/api/analytics";

const keys = {
  detail: (o: string, p: string, windowDays: number) =>
    ["organizations", o, "projects", p, "analytics", windowDays] as const,
};

export function useAnalytics(
  organizationId: string | undefined,
  projectId: string | undefined,
  windowDays = 30,
) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? "", windowDays),
    queryFn: () => analyticsApi.get(organizationId as string, projectId as string, windowDays),
    enabled: !!organizationId && !!projectId,
  });
}
