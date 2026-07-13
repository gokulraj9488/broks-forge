"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PageParams } from "@/lib/api/organizations";
import {
  evaluationJobsApi,
  type CreateEvaluationJobPayload,
  type EvaluationJobFilterParams,
  type EvaluationJobResponse,
} from "@/lib/api/evaluation-jobs";

const keys = {
  scope: (o: string, p: string) =>
    ["organizations", o, "projects", p, "evaluation-jobs"] as const,
  list: (o: string, p: string, params: EvaluationJobFilterParams) =>
    ["organizations", o, "projects", p, "evaluation-jobs", "list", params] as const,
  detail: (o: string, p: string, j: string) =>
    ["organizations", o, "projects", p, "evaluation-jobs", j] as const,
  runs: (o: string, p: string, j: string) =>
    ["organizations", o, "projects", p, "evaluation-jobs", j, "runs"] as const,
  results: (o: string, p: string, j: string, r: string) =>
    ["organizations", o, "projects", p, "evaluation-jobs", j, "runs", r, "results"] as const,
};

const isActive = (status: string | undefined) =>
  status === "PENDING" || status === "RUNNING";

export function useEvaluationJobs(
  organizationId: string | undefined,
  projectId: string | undefined,
  params: EvaluationJobFilterParams = {},
) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", projectId ?? "", params),
    queryFn: () => evaluationJobsApi.list(organizationId as string, projectId as string, params),
    enabled: !!organizationId && !!projectId,
    // Keep the list fresh while any job is still in flight.
    refetchInterval: (query) =>
      (query.state.data?.content ?? []).some((j) => isActive(j.status)) ? 4000 : false,
  });
}

export function useEvaluationJob(
  organizationId: string | undefined,
  projectId: string | undefined,
  jobId: string | undefined,
) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? "", jobId ?? ""),
    queryFn: () =>
      evaluationJobsApi.get(organizationId as string, projectId as string, jobId as string),
    enabled: !!organizationId && !!projectId && !!jobId,
    refetchInterval: (query) =>
      isActive((query.state.data as EvaluationJobResponse | undefined)?.status) ? 3000 : false,
  });
}

export function useCreateEvaluationJob(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateEvaluationJobPayload) =>
      evaluationJobsApi.create(organizationId, projectId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useDeleteEvaluationJob(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (jobId: string) => evaluationJobsApi.remove(organizationId, projectId, jobId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useEvaluationJobAction(
  organizationId: string,
  projectId: string,
  jobId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (action: "run" | "cancel" | "resume") => {
      if (action === "run") return evaluationJobsApi.run(organizationId, projectId, jobId);
      if (action === "resume") return evaluationJobsApi.resume(organizationId, projectId, jobId);
      return evaluationJobsApi.cancel(organizationId, projectId, jobId);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.detail(organizationId, projectId, jobId) });
      qc.invalidateQueries({ queryKey: keys.runs(organizationId, projectId, jobId) });
      qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) });
    },
  });
}

export function useEvaluationRuns(
  organizationId: string | undefined,
  projectId: string | undefined,
  jobId: string | undefined,
  params: PageParams = {},
  jobActive = false,
) {
  return useQuery({
    queryKey: [...keys.runs(organizationId ?? "", projectId ?? "", jobId ?? ""), params],
    queryFn: () =>
      evaluationJobsApi.listRuns(
        organizationId as string,
        projectId as string,
        jobId as string,
        params,
      ),
    enabled: !!organizationId && !!projectId && !!jobId,
    refetchInterval: jobActive ? 3000 : false,
  });
}

export function useEvaluationRunResults(
  organizationId: string | undefined,
  projectId: string | undefined,
  jobId: string | undefined,
  runId: string | undefined,
) {
  return useQuery({
    queryKey: keys.results(organizationId ?? "", projectId ?? "", jobId ?? "", runId ?? ""),
    queryFn: () =>
      evaluationJobsApi.runResults(
        organizationId as string,
        projectId as string,
        jobId as string,
        runId as string,
      ),
    enabled: !!organizationId && !!projectId && !!jobId && !!runId,
  });
}
