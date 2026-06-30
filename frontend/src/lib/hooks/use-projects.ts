"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PageParams } from "@/lib/api/organizations";
import {
  projectsApi,
  type CreateProjectPayload,
  type UpdateProjectPayload,
} from "@/lib/api/projects";

const keys = {
  all: (organizationId: string) => ["organizations", organizationId, "projects"] as const,
  list: (organizationId: string, params: PageParams) =>
    ["organizations", organizationId, "projects", "list", params] as const,
  detail: (organizationId: string, projectId: string) =>
    ["organizations", organizationId, "projects", projectId] as const,
};

export function useProjects(organizationId: string | undefined, params: PageParams = {}) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", params),
    queryFn: () => projectsApi.list(organizationId as string, params),
    enabled: !!organizationId,
  });
}

export function useProject(organizationId: string | undefined, projectId: string | undefined) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? ""),
    queryFn: () => projectsApi.get(organizationId as string, projectId as string),
    enabled: !!organizationId && !!projectId,
  });
}

export function useCreateProject(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateProjectPayload) => projectsApi.create(organizationId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all(organizationId) }),
  });
}

export function useUpdateProject(organizationId: string, projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateProjectPayload) =>
      projectsApi.update(organizationId, projectId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all(organizationId) }),
  });
}

export function useDeleteProject(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (projectId: string) => projectsApi.remove(organizationId, projectId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all(organizationId) }),
  });
}
