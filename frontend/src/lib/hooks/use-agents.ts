"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PageParams } from "@/lib/api/organizations";
import {
  agentsApi,
  type AgentFilterParams,
  type RegisterAgentPayload,
  type RegisterAgentVersionPayload,
  type SetAgentCredentialPayload,
  type UpdateAgentPayload,
} from "@/lib/api/agents";

const keys = {
  scope: (o: string, p: string) => ["organizations", o, "projects", p, "agents"] as const,
  list: (o: string, p: string, params: AgentFilterParams) =>
    ["organizations", o, "projects", p, "agents", "list", params] as const,
  detail: (o: string, p: string, a: string) =>
    ["organizations", o, "projects", p, "agents", a] as const,
  versions: (o: string, p: string, a: string) =>
    ["organizations", o, "projects", p, "agents", a, "versions"] as const,
  credentials: (o: string, p: string, a: string) =>
    ["organizations", o, "projects", p, "agents", a, "credentials"] as const,
  health: (o: string, p: string, a: string) =>
    ["organizations", o, "projects", p, "agents", a, "health"] as const,
};

// --------------------------------------------------------------------------
// Agents
// --------------------------------------------------------------------------
export function useAgents(
  organizationId: string | undefined,
  projectId: string | undefined,
  params: AgentFilterParams = {},
) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", projectId ?? "", params),
    queryFn: () => agentsApi.list(organizationId as string, projectId as string, params),
    enabled: !!organizationId && !!projectId,
  });
}

export function useAgent(
  organizationId: string | undefined,
  projectId: string | undefined,
  agentId: string | undefined,
) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? "", agentId ?? ""),
    queryFn: () => agentsApi.get(organizationId as string, projectId as string, agentId as string),
    enabled: !!organizationId && !!projectId && !!agentId,
  });
}

export function useCreateAgent(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: RegisterAgentPayload) => agentsApi.create(organizationId, projectId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useUpdateAgent(organizationId: string, projectId: string, agentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateAgentPayload) =>
      agentsApi.update(organizationId, projectId, agentId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useDeleteAgent(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (agentId: string) => agentsApi.remove(organizationId, projectId, agentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useArchiveAgent(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ agentId, archive }: { agentId: string; archive: boolean }) =>
      archive
        ? agentsApi.archive(organizationId, projectId, agentId)
        : agentsApi.unarchive(organizationId, projectId, agentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

// --------------------------------------------------------------------------
// Versions
// --------------------------------------------------------------------------
export function useAgentVersions(
  organizationId: string | undefined,
  projectId: string | undefined,
  agentId: string | undefined,
  params: PageParams = {},
) {
  return useQuery({
    queryKey: [...keys.versions(organizationId ?? "", projectId ?? "", agentId ?? ""), params],
    queryFn: () =>
      agentsApi.listVersions(organizationId as string, projectId as string, agentId as string, params),
    enabled: !!organizationId && !!projectId && !!agentId,
  });
}

export function useRegisterVersion(organizationId: string, projectId: string, agentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: RegisterAgentVersionPayload) =>
      agentsApi.registerVersion(organizationId, projectId, agentId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.versions(organizationId, projectId, agentId) });
      qc.invalidateQueries({ queryKey: keys.detail(organizationId, projectId, agentId) });
    },
  });
}

export function useVersionAction(organizationId: string, projectId: string, agentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ versionId, action }: { versionId: string; action: "activate" | "rollback" }) =>
      action === "activate"
        ? agentsApi.activateVersion(organizationId, projectId, agentId, versionId)
        : agentsApi.rollbackVersion(organizationId, projectId, agentId, versionId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.versions(organizationId, projectId, agentId) });
      qc.invalidateQueries({ queryKey: keys.detail(organizationId, projectId, agentId) });
    },
  });
}

// --------------------------------------------------------------------------
// Credentials
// --------------------------------------------------------------------------
export function useAgentCredentials(
  organizationId: string | undefined,
  projectId: string | undefined,
  agentId: string | undefined,
) {
  return useQuery({
    queryKey: keys.credentials(organizationId ?? "", projectId ?? "", agentId ?? ""),
    queryFn: () =>
      agentsApi.listCredentials(organizationId as string, projectId as string, agentId as string),
    enabled: !!organizationId && !!projectId && !!agentId,
  });
}

export function useSetCredential(organizationId: string, projectId: string, agentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: SetAgentCredentialPayload) =>
      agentsApi.setCredential(organizationId, projectId, agentId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.credentials(organizationId, projectId, agentId) });
      qc.invalidateQueries({ queryKey: keys.detail(organizationId, projectId, agentId) });
    },
  });
}

export function useDeleteCredential(organizationId: string, projectId: string, agentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (credentialId: string) =>
      agentsApi.deleteCredential(organizationId, projectId, agentId, credentialId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.credentials(organizationId, projectId, agentId) }),
  });
}

// --------------------------------------------------------------------------
// Health
// --------------------------------------------------------------------------
export function useAgentHealth(
  organizationId: string | undefined,
  projectId: string | undefined,
  agentId: string | undefined,
) {
  return useQuery({
    queryKey: keys.health(organizationId ?? "", projectId ?? "", agentId ?? ""),
    queryFn: () => agentsApi.getHealth(organizationId as string, projectId as string, agentId as string),
    enabled: !!organizationId && !!projectId && !!agentId,
  });
}

export function useRunHealthCheck(organizationId: string, projectId: string, agentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => agentsApi.runHealthCheck(organizationId, projectId, agentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.health(organizationId, projectId, agentId) });
      qc.invalidateQueries({ queryKey: keys.detail(organizationId, projectId, agentId) });
      qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) });
    },
  });
}
