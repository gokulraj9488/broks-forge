"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PageParams } from "@/lib/api/organizations";
import {
  evaluationProfilesApi,
  type CreateEvaluationProfilePayload,
  type UpdateEvaluationProfilePayload,
} from "@/lib/api/evaluation-profiles";

const keys = {
  scope: (o: string, p: string) =>
    ["organizations", o, "projects", p, "evaluation-profiles"] as const,
  list: (o: string, p: string, params: PageParams) =>
    ["organizations", o, "projects", p, "evaluation-profiles", "list", params] as const,
  detail: (o: string, p: string, id: string) =>
    ["organizations", o, "projects", p, "evaluation-profiles", id] as const,
};

export function useEvaluationProfiles(
  organizationId: string | undefined,
  projectId: string | undefined,
  params: PageParams = {},
) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", projectId ?? "", params),
    queryFn: () =>
      evaluationProfilesApi.list(organizationId as string, projectId as string, params),
    enabled: !!organizationId && !!projectId,
  });
}

export function useEvaluationProfile(
  organizationId: string | undefined,
  projectId: string | undefined,
  profileId: string | undefined,
) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? "", profileId ?? ""),
    queryFn: () =>
      evaluationProfilesApi.get(
        organizationId as string,
        projectId as string,
        profileId as string,
      ),
    enabled: !!organizationId && !!projectId && !!profileId,
  });
}

export function useCreateEvaluationProfile(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateEvaluationProfilePayload) =>
      evaluationProfilesApi.create(organizationId, projectId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useUpdateEvaluationProfile(
  organizationId: string,
  projectId: string,
  profileId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateEvaluationProfilePayload) =>
      evaluationProfilesApi.update(organizationId, projectId, profileId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useDeleteEvaluationProfile(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (profileId: string) =>
      evaluationProfilesApi.remove(organizationId, projectId, profileId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}
