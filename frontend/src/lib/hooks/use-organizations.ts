"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  organizationsApi,
  type CreateOrganizationPayload,
  type PageParams,
  type UpdateOrganizationPayload,
} from "@/lib/api/organizations";
import type { OrganizationRole } from "@/lib/api/types";

const keys = {
  all: ["organizations"] as const,
  list: (params: PageParams) => ["organizations", "list", params] as const,
  detail: (id: string) => ["organizations", id] as const,
  members: (id: string, params: PageParams) => ["organizations", id, "members", params] as const,
};

export function useOrganizations(params: PageParams = {}) {
  return useQuery({
    queryKey: keys.list(params),
    queryFn: () => organizationsApi.list(params),
  });
}

export function useOrganization(id: string | undefined) {
  return useQuery({
    queryKey: keys.detail(id ?? ""),
    queryFn: () => organizationsApi.get(id as string),
    enabled: !!id,
  });
}

export function useCreateOrganization() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateOrganizationPayload) => organizationsApi.create(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all }),
  });
}

export function useUpdateOrganization(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateOrganizationPayload) => organizationsApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.all });
      queryClient.invalidateQueries({ queryKey: keys.detail(id) });
    },
  });
}

export function useDeleteOrganization() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => organizationsApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all }),
  });
}

export function useOrganizationMembers(organizationId: string | undefined, params: PageParams = {}) {
  return useQuery({
    queryKey: keys.members(organizationId ?? "", params),
    queryFn: () => organizationsApi.listMembers(organizationId as string, params),
    enabled: !!organizationId,
  });
}

export function useAddMember(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ email, role }: { email: string; role: OrganizationRole }) =>
      organizationsApi.addMember(organizationId, email, role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "members"] });
      queryClient.invalidateQueries({ queryKey: keys.detail(organizationId) });
    },
  });
}

export function useUpdateMemberRole(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: OrganizationRole }) =>
      organizationsApi.updateMemberRole(organizationId, userId, role),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "members"] }),
  });
}

export function useRemoveMember(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => organizationsApi.removeMember(organizationId, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "members"] });
      queryClient.invalidateQueries({ queryKey: keys.detail(organizationId) });
    },
  });
}
