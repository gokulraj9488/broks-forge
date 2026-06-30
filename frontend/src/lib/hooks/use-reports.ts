"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PageParams } from "@/lib/api/organizations";
import { reportsApi, type ExportReportPayload } from "@/lib/api/reports";

const keys = {
  scope: (o: string, p: string) => ["organizations", o, "projects", p, "reports"] as const,
  list: (o: string, p: string, params: PageParams) =>
    ["organizations", o, "projects", p, "reports", "list", params] as const,
};

export function useReports(
  organizationId: string | undefined,
  projectId: string | undefined,
  params: PageParams = {},
) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", projectId ?? "", params),
    queryFn: () => reportsApi.list(organizationId as string, projectId as string, params),
    enabled: !!organizationId && !!projectId,
  });
}

export function useExportReport(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ExportReportPayload) =>
      reportsApi.export(organizationId, projectId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}
