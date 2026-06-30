"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PageParams } from "@/lib/api/organizations";
import {
  regressionApi,
  type CreateRegressionCheckPayload,
} from "@/lib/api/regression";

const keys = {
  scope: (o: string, p: string) =>
    ["organizations", o, "projects", p, "regression-checks"] as const,
  list: (o: string, p: string, params: PageParams) =>
    ["organizations", o, "projects", p, "regression-checks", "list", params] as const,
  detail: (o: string, p: string, c: string) =>
    ["organizations", o, "projects", p, "regression-checks", c] as const,
};

export function useRegressionChecks(
  organizationId: string | undefined,
  projectId: string | undefined,
  params: PageParams = {},
) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", projectId ?? "", params),
    queryFn: () => regressionApi.list(organizationId as string, projectId as string, params),
    enabled: !!organizationId && !!projectId,
  });
}

export function useRegressionCheck(
  organizationId: string | undefined,
  projectId: string | undefined,
  checkId: string | undefined,
) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? "", checkId ?? ""),
    queryFn: () =>
      regressionApi.get(organizationId as string, projectId as string, checkId as string),
    enabled: !!organizationId && !!projectId && !!checkId,
  });
}

export function useCreateRegressionCheck(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateRegressionCheckPayload) =>
      regressionApi.create(organizationId, projectId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useDeleteRegressionCheck(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (checkId: string) => regressionApi.remove(organizationId, projectId, checkId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}
