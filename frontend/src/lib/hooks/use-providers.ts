"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PageParams } from "@/lib/api/organizations";
import { providersApi, type CreateProviderPayload, type UpdateProviderPayload } from "@/lib/api/providers";

const keys = {
  scope: (o: string, p: string) => ["organizations", o, "projects", p, "providers"] as const,
  list: (o: string, p: string, params: PageParams) =>
    ["organizations", o, "projects", p, "providers", "list", params] as const,
  detail: (o: string, p: string, id: string) => ["organizations", o, "projects", p, "providers", id] as const,
  embeddingModels: (o: string, p: string, id: string) =>
    ["organizations", o, "projects", p, "providers", id, "embedding-models"] as const,
  chatModels: (o: string, p: string, id: string) =>
    ["organizations", o, "projects", p, "providers", id, "chat-models"] as const,
};

export function useProviders(
  organizationId: string | undefined,
  projectId: string | undefined,
  params: PageParams = {},
) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", projectId ?? "", params),
    queryFn: () => providersApi.list(organizationId as string, projectId as string, params),
    enabled: !!organizationId && !!projectId,
  });
}

export function useProvider(
  organizationId: string | undefined,
  projectId: string | undefined,
  providerId: string | undefined,
) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? "", providerId ?? ""),
    queryFn: () => providersApi.get(organizationId as string, projectId as string, providerId as string),
    enabled: !!organizationId && !!projectId && !!providerId,
  });
}

export function useCreateProvider(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateProviderPayload) => providersApi.create(organizationId, projectId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useUpdateProvider(organizationId: string, projectId: string, providerId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateProviderPayload) =>
      providersApi.update(organizationId, projectId, providerId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useDeleteProvider(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (providerId: string) => providersApi.remove(organizationId, projectId, providerId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useDuplicateProvider(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (providerId: string) => providersApi.duplicate(organizationId, projectId, providerId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

/** Embedding models actually available for a provider (Semantic Similarity metric editor). */
export function useEmbeddingModels(
  organizationId: string | undefined,
  projectId: string | undefined,
  providerId: string | undefined,
) {
  return useQuery({
    queryKey: keys.embeddingModels(organizationId ?? "", projectId ?? "", providerId ?? ""),
    queryFn: () =>
      providersApi.embeddingModels(organizationId as string, projectId as string, providerId as string),
    enabled: !!organizationId && !!projectId && !!providerId,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}

/** Chat/judge-capable models actually available for a provider (LLM Judge/Hallucination/Citation editors). */
export function useChatModels(
  organizationId: string | undefined,
  projectId: string | undefined,
  providerId: string | undefined,
) {
  return useQuery({
    queryKey: keys.chatModels(organizationId ?? "", projectId ?? "", providerId ?? ""),
    queryFn: () => providersApi.chatModels(organizationId as string, projectId as string, providerId as string),
    enabled: !!organizationId && !!projectId && !!providerId,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}

/** One-shot reachability/credential test against a stored provider's own endpoint. */
export function useTestProviderConnection(organizationId: string, projectId: string) {
  return useMutation({
    mutationFn: (providerId: string) => providersApi.testConnection(organizationId, projectId, providerId),
  });
}

/**
 * On-demand "Refresh models" action for a provider card: re-runs live model discovery
 * (the same discovery {@link useChatModels} exposes for the metric editor) and refreshes the
 * cached result, so a provider list doesn't eagerly query every provider's models endpoint on
 * page load — only when the user explicitly asks.
 */
export function useRefreshProviderModels(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (providerId: string) => providersApi.chatModels(organizationId, projectId, providerId),
    onSuccess: (_data, providerId) =>
      qc.invalidateQueries({ queryKey: keys.chatModels(organizationId, projectId, providerId) }),
  });
}

export function useToggleProviderEnabled(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ providerId, enabled }: { providerId: string; enabled: boolean }) =>
      enabled
        ? providersApi.enable(organizationId, projectId, providerId)
        : providersApi.disable(organizationId, projectId, providerId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}
