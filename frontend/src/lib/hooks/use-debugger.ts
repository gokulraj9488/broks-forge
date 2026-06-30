"use client";

import { useQuery } from "@tanstack/react-query";
import { debuggerApi } from "@/lib/api/debugger";

const keys = {
  timeline: (o: string, p: string, j: string, r: string) =>
    ["organizations", o, "projects", p, "debugger", "jobs", j, "runs", r, "timeline"] as const,
};

export function useExecutionTimeline(
  organizationId: string | undefined,
  projectId: string | undefined,
  jobId: string | undefined,
  runId: string | undefined,
  enabled = true,
) {
  return useQuery({
    queryKey: keys.timeline(
      organizationId ?? "",
      projectId ?? "",
      jobId ?? "",
      runId ?? "",
    ),
    queryFn: () =>
      debuggerApi.timeline(
        organizationId as string,
        projectId as string,
        jobId as string,
        runId as string,
      ),
    enabled: enabled && !!organizationId && !!projectId && !!jobId && !!runId,
  });
}
