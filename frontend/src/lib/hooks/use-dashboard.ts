"use client";

import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboard";

const keys = {
  detail: (o: string, p: string) =>
    ["organizations", o, "projects", p, "dashboard"] as const,
};

export function useDashboard(
  organizationId: string | undefined,
  projectId: string | undefined,
) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? ""),
    queryFn: () => dashboardApi.get(organizationId as string, projectId as string),
    enabled: !!organizationId && !!projectId,
  });
}
