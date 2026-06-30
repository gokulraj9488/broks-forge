import { apiClient } from "@/lib/api/client";
import type {
  OrganizationMemberResponse,
  OrganizationResponse,
  OrganizationRole,
  PageResponse,
} from "@/lib/api/types";

export interface CreateOrganizationPayload {
  name: string;
  slug?: string;
  description?: string;
}

export interface UpdateOrganizationPayload {
  name?: string;
  description?: string;
  status?: "ACTIVE" | "ARCHIVED";
}

export interface PageParams {
  page?: number;
  size?: number;
}

export const organizationsApi = {
  list: (params: PageParams = {}) =>
    apiClient
      .get<PageResponse<OrganizationResponse>>("/api/v1/organizations", { params })
      .then((r) => r.data),

  get: (id: string) =>
    apiClient.get<OrganizationResponse>(`/api/v1/organizations/${id}`).then((r) => r.data),

  create: (payload: CreateOrganizationPayload) =>
    apiClient.post<OrganizationResponse>("/api/v1/organizations", payload).then((r) => r.data),

  update: (id: string, payload: UpdateOrganizationPayload) =>
    apiClient.patch<OrganizationResponse>(`/api/v1/organizations/${id}`, payload).then((r) => r.data),

  remove: (id: string) =>
    apiClient.delete<void>(`/api/v1/organizations/${id}`).then((r) => r.data),

  listMembers: (organizationId: string, params: PageParams = {}) =>
    apiClient
      .get<PageResponse<OrganizationMemberResponse>>(
        `/api/v1/organizations/${organizationId}/members`,
        { params },
      )
      .then((r) => r.data),

  addMember: (organizationId: string, email: string, role: OrganizationRole) =>
    apiClient
      .post<OrganizationMemberResponse>(`/api/v1/organizations/${organizationId}/members`, {
        email,
        role,
      })
      .then((r) => r.data),

  updateMemberRole: (organizationId: string, userId: string, role: OrganizationRole) =>
    apiClient
      .patch<OrganizationMemberResponse>(
        `/api/v1/organizations/${organizationId}/members/${userId}`,
        { role },
      )
      .then((r) => r.data),

  removeMember: (organizationId: string, userId: string) =>
    apiClient
      .delete<void>(`/api/v1/organizations/${organizationId}/members/${userId}`)
      .then((r) => r.data),
};
