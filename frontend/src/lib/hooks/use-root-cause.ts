"use client";

import { useQuery } from "@tanstack/react-query";
import { rootCauseApi } from "@/lib/api/root-cause";

const keys = {
  job: (o: string, p: string, j: string) =>
    ["organizations", o, "projects", p, "root-cause", "job", j] as const,
  regression: (o: string, p: string, c: string) =>
    ["organizations", o, "projects", p, "root-cause", "regression", c] as const,
};

export function useJobRootCause(
  organizationId: string | undefined,
  projectId: string | undefined,
  jobId: string | undefined,
) {
  return useQuery({
    queryKey: keys.job(organizationId ?? "", projectId ?? "", jobId ?? ""),
    queryFn: () =>
      rootCauseApi.forJob(organizationId as string, projectId as string, jobId as string),
    enabled: !!organizationId && !!projectId && !!jobId,
  });
}

export function useRegressionRootCause(
  organizationId: string | undefined,
  projectId: string | undefined,
  checkId: string | undefined,
) {
  return useQuery({
    queryKey: keys.regression(organizationId ?? "", projectId ?? "", checkId ?? ""),
    queryFn: () =>
      rootCauseApi.forRegression(organizationId as string, projectId as string, checkId as string),
    enabled: !!organizationId && !!projectId && !!checkId,
  });
}
