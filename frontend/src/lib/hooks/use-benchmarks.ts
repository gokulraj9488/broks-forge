"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PageParams } from "@/lib/api/organizations";
import {
  benchmarksApi,
  type CreateBenchmarkEntryInput,
  type CreateBenchmarkPayload,
} from "@/lib/api/benchmarks";

const keys = {
  scope: (o: string, p: string) => ["organizations", o, "projects", p, "benchmarks"] as const,
  list: (o: string, p: string, params: PageParams) =>
    ["organizations", o, "projects", p, "benchmarks", "list", params] as const,
  detail: (o: string, p: string, b: string) =>
    ["organizations", o, "projects", p, "benchmarks", b] as const,
  leaderboard: (o: string, p: string, b: string) =>
    ["organizations", o, "projects", p, "benchmarks", b, "leaderboard"] as const,
};

export function useBenchmarks(
  organizationId: string | undefined,
  projectId: string | undefined,
  params: PageParams = {},
) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", projectId ?? "", params),
    queryFn: () => benchmarksApi.list(organizationId as string, projectId as string, params),
    enabled: !!organizationId && !!projectId,
  });
}

export function useBenchmark(
  organizationId: string | undefined,
  projectId: string | undefined,
  benchmarkId: string | undefined,
) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? "", benchmarkId ?? ""),
    queryFn: () =>
      benchmarksApi.get(organizationId as string, projectId as string, benchmarkId as string),
    enabled: !!organizationId && !!projectId && !!benchmarkId,
  });
}

export function useBenchmarkLeaderboard(
  organizationId: string | undefined,
  projectId: string | undefined,
  benchmarkId: string | undefined,
) {
  return useQuery({
    queryKey: keys.leaderboard(organizationId ?? "", projectId ?? "", benchmarkId ?? ""),
    queryFn: () =>
      benchmarksApi.leaderboard(
        organizationId as string,
        projectId as string,
        benchmarkId as string,
      ),
    enabled: !!organizationId && !!projectId && !!benchmarkId,
  });
}

export function useCreateBenchmark(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateBenchmarkPayload) =>
      benchmarksApi.create(organizationId, projectId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useDeleteBenchmark(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (benchmarkId: string) =>
      benchmarksApi.remove(organizationId, projectId, benchmarkId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useAddBenchmarkEntry(
  organizationId: string,
  projectId: string,
  benchmarkId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateBenchmarkEntryInput) =>
      benchmarksApi.addEntry(organizationId, projectId, benchmarkId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.detail(organizationId, projectId, benchmarkId) });
      qc.invalidateQueries({ queryKey: keys.leaderboard(organizationId, projectId, benchmarkId) });
    },
  });
}

export function useRemoveBenchmarkEntry(
  organizationId: string,
  projectId: string,
  benchmarkId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (entryId: string) =>
      benchmarksApi.removeEntry(organizationId, projectId, benchmarkId, entryId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.detail(organizationId, projectId, benchmarkId) });
      qc.invalidateQueries({ queryKey: keys.leaderboard(organizationId, projectId, benchmarkId) });
    },
  });
}
