import { apiClient } from "@/lib/api/client";
import type { PageParams } from "@/lib/api/organizations";
import type { PageResponse, ProjectResponse } from "@/lib/api/types";

export interface CreateProjectPayload {
  name: string;
  slug?: string;
  description?: string;
}

export interface UpdateProjectPayload {
  name?: string;
  description?: string;
  status?: "ACTIVE" | "ARCHIVED";
}

export const projectsApi = {
  list: (organizationId: string, params: PageParams = {}) =>
    apiClient
      .get<PageResponse<ProjectResponse>>(
        `/api/v1/organizations/${organizationId}/projects`,
        { params },
      )
      .then((r) => r.data),

  get: (organizationId: string, projectId: string) =>
    apiClient
      .get<ProjectResponse>(`/api/v1/organizations/${organizationId}/projects/${projectId}`)
      .then((r) => r.data),

  create: (organizationId: string, payload: CreateProjectPayload) =>
    apiClient
      .post<ProjectResponse>(`/api/v1/organizations/${organizationId}/projects`, payload)
      .then((r) => r.data),

  update: (organizationId: string, projectId: string, payload: UpdateProjectPayload) =>
    apiClient
      .patch<ProjectResponse>(
        `/api/v1/organizations/${organizationId}/projects/${projectId}`,
        payload,
      )
      .then((r) => r.data),

  remove: (organizationId: string, projectId: string) =>
    apiClient
      .delete<void>(`/api/v1/organizations/${organizationId}/projects/${projectId}`)
      .then((r) => r.data),
};
