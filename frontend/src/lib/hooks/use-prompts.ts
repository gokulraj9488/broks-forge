"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PageParams } from "@/lib/api/organizations";
import {
  promptsApi,
  type CreatePromptPayload,
  type CreatePromptVersionPayload,
  type PromptFilterParams,
  type UpdatePromptPayload,
} from "@/lib/api/prompts";

const keys = {
  scope: (o: string, p: string) => ["organizations", o, "projects", p, "prompts"] as const,
  list: (o: string, p: string, params: PromptFilterParams) =>
    ["organizations", o, "projects", p, "prompts", "list", params] as const,
  detail: (o: string, p: string, pr: string) =>
    ["organizations", o, "projects", p, "prompts", pr] as const,
  versions: (o: string, p: string, pr: string) =>
    ["organizations", o, "projects", p, "prompts", pr, "versions"] as const,
  compare: (o: string, p: string, pr: string, from: string, to: string) =>
    ["organizations", o, "projects", p, "prompts", pr, "compare", from, to] as const,
};

export function usePrompts(
  organizationId: string | undefined,
  projectId: string | undefined,
  params: PromptFilterParams = {},
) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", projectId ?? "", params),
    queryFn: () => promptsApi.list(organizationId as string, projectId as string, params),
    enabled: !!organizationId && !!projectId,
  });
}

export function usePrompt(
  organizationId: string | undefined,
  projectId: string | undefined,
  promptId: string | undefined,
) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? "", promptId ?? ""),
    queryFn: () =>
      promptsApi.get(organizationId as string, projectId as string, promptId as string),
    enabled: !!organizationId && !!projectId && !!promptId,
  });
}

export function useCreatePrompt(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreatePromptPayload) =>
      promptsApi.create(organizationId, projectId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useUpdatePrompt(organizationId: string, projectId: string, promptId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdatePromptPayload) =>
      promptsApi.update(organizationId, projectId, promptId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useDeletePrompt(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (promptId: string) => promptsApi.remove(organizationId, projectId, promptId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useArchivePrompt(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ promptId, archive }: { promptId: string; archive: boolean }) =>
      archive
        ? promptsApi.archive(organizationId, projectId, promptId)
        : promptsApi.unarchive(organizationId, projectId, promptId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function usePromptVersions(
  organizationId: string | undefined,
  projectId: string | undefined,
  promptId: string | undefined,
  params: PageParams = {},
) {
  return useQuery({
    queryKey: [...keys.versions(organizationId ?? "", projectId ?? "", promptId ?? ""), params],
    queryFn: () =>
      promptsApi.listVersions(
        organizationId as string,
        projectId as string,
        promptId as string,
        params,
      ),
    enabled: !!organizationId && !!projectId && !!promptId,
  });
}

export function useCreatePromptVersion(
  organizationId: string,
  projectId: string,
  promptId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreatePromptVersionPayload) =>
      promptsApi.createVersion(organizationId, projectId, promptId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.versions(organizationId, projectId, promptId) });
      qc.invalidateQueries({ queryKey: keys.detail(organizationId, projectId, promptId) });
    },
  });
}

export function usePromptVersionAction(
  organizationId: string,
  projectId: string,
  promptId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      versionId,
      action,
    }: {
      versionId: string;
      action: "activate" | "rollback";
    }) =>
      action === "activate"
        ? promptsApi.activateVersion(organizationId, projectId, promptId, versionId)
        : promptsApi.rollbackVersion(organizationId, projectId, promptId, versionId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.versions(organizationId, projectId, promptId) });
      qc.invalidateQueries({ queryKey: keys.detail(organizationId, projectId, promptId) });
    },
  });
}

export function usePromptCompare(
  organizationId: string | undefined,
  projectId: string | undefined,
  promptId: string | undefined,
  from: string | undefined,
  to: string | undefined,
) {
  return useQuery({
    queryKey: keys.compare(
      organizationId ?? "",
      projectId ?? "",
      promptId ?? "",
      from ?? "",
      to ?? "",
    ),
    queryFn: () =>
      promptsApi.compare(
        organizationId as string,
        projectId as string,
        promptId as string,
        from as string,
        to as string,
      ),
    enabled:
      !!organizationId &&
      !!projectId &&
      !!promptId &&
      from != null &&
      to != null &&
      from !== to,
  });
}
