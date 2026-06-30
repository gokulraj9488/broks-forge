"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiKeysApi, type CreateApiKeyPayload } from "@/lib/api/api-keys";
import type { PageParams } from "@/lib/api/organizations";

const keys = {
  all: (organizationId: string, projectId: string) =>
    ["organizations", organizationId, "projects", projectId, "api-keys"] as const,
  list: (organizationId: string, projectId: string, params: PageParams) =>
    ["organizations", organizationId, "projects", projectId, "api-keys", "list", params] as const,
};

export function useApiKeys(
  organizationId: string | undefined,
  projectId: string | undefined,
  params: PageParams = {},
) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", projectId ?? "", params),
    queryFn: () => apiKeysApi.list(organizationId as string, projectId as string, params),
    enabled: !!organizationId && !!projectId,
  });
}

export function useCreateApiKey(organizationId: string, projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateApiKeyPayload) =>
      apiKeysApi.create(organizationId, projectId, payload),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: keys.all(organizationId, projectId) }),
  });
}

export function useRevokeApiKey(organizationId: string, projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (apiKeyId: string) => apiKeysApi.revoke(organizationId, projectId, apiKeyId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: keys.all(organizationId, projectId) }),
  });
}
