import { apiClient } from "@/lib/api/client";
import type { PageParams } from "@/lib/api/organizations";
import type { PageResponse } from "@/lib/api/types";
import type { LlmProvider } from "@/lib/api/agents";

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------
export type PromptStatus = "ACTIVE" | "ARCHIVED";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface PromptSummaryResponse {
  id: string;
  name: string;
  slug: string;
  status: PromptStatus;
  tags: string[];
  latestVersionNumber: number | null;
  currentActiveVersionId: string | null;
  updatedAt: string;
}

export interface PromptResponse extends PromptSummaryResponse {
  organizationId: string;
  projectId: string;
  description: string | null;
  ownerId: string;
  createdAt: string;
}

export interface PromptVersionResponse {
  id: string;
  promptId: string;
  versionNumber: number;
  template: string;
  variables: string[];
  notes: string | null;
  provider: LlmProvider | null;
  model: string | null;
  active: boolean;
  createdAt: string;
}

export interface PromptCompareResponse {
  from: number;
  to: number;
  addedVariables: string[];
  removedVariables: string[];
  commonVariables: string[];
  identicalTemplate: boolean;
}

// ---------------------------------------------------------------------------
// Request payloads
// ---------------------------------------------------------------------------
export interface CreatePromptPayload {
  name: string;
  slug?: string;
  description?: string;
  tags?: string[];
}

export type UpdatePromptPayload = Partial<{
  name: string;
  description: string;
  tags: string[];
}>;

export interface CreatePromptVersionPayload {
  template: string;
  notes?: string;
  provider?: LlmProvider;
  model?: string;
  activate?: boolean;
}

export interface PromptFilterParams extends PageParams {
  q?: string;
  status?: PromptStatus;
  tag?: string;
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/prompts`;
}

export const promptsApi = {
  list: (organizationId: string, projectId: string, params: PromptFilterParams = {}) =>
    apiClient
      .get<PageResponse<PromptSummaryResponse>>(base(organizationId, projectId), { params })
      .then((r) => r.data),

  get: (organizationId: string, projectId: string, promptId: string) =>
    apiClient
      .get<PromptResponse>(`${base(organizationId, projectId)}/${promptId}`)
      .then((r) => r.data),

  create: (organizationId: string, projectId: string, payload: CreatePromptPayload) =>
    apiClient
      .post<PromptResponse>(base(organizationId, projectId), payload)
      .then((r) => r.data),

  update: (
    organizationId: string,
    projectId: string,
    promptId: string,
    payload: UpdatePromptPayload,
  ) =>
    apiClient
      .patch<PromptResponse>(`${base(organizationId, projectId)}/${promptId}`, payload)
      .then((r) => r.data),

  remove: (organizationId: string, projectId: string, promptId: string) =>
    apiClient
      .delete<void>(`${base(organizationId, projectId)}/${promptId}`)
      .then((r) => r.data),

  archive: (organizationId: string, projectId: string, promptId: string) =>
    apiClient
      .post<PromptResponse>(`${base(organizationId, projectId)}/${promptId}/archive`)
      .then((r) => r.data),

  unarchive: (organizationId: string, projectId: string, promptId: string) =>
    apiClient
      .post<PromptResponse>(`${base(organizationId, projectId)}/${promptId}/unarchive`)
      .then((r) => r.data),

  createVersion: (
    organizationId: string,
    projectId: string,
    promptId: string,
    payload: CreatePromptVersionPayload,
  ) =>
    apiClient
      .post<PromptVersionResponse>(
        `${base(organizationId, projectId)}/${promptId}/versions`,
        payload,
      )
      .then((r) => r.data),

  listVersions: (
    organizationId: string,
    projectId: string,
    promptId: string,
    params: PageParams = {},
  ) =>
    apiClient
      .get<PageResponse<PromptVersionResponse>>(
        `${base(organizationId, projectId)}/${promptId}/versions`,
        { params },
      )
      .then((r) => r.data),

  activateVersion: (
    organizationId: string,
    projectId: string,
    promptId: string,
    versionId: string,
  ) =>
    apiClient
      .post<PromptVersionResponse>(
        `${base(organizationId, projectId)}/${promptId}/versions/${versionId}/activate`,
      )
      .then((r) => r.data),

  rollbackVersion: (
    organizationId: string,
    projectId: string,
    promptId: string,
    versionId: string,
  ) =>
    apiClient
      .post<PromptVersionResponse>(
        `${base(organizationId, projectId)}/${promptId}/versions/${versionId}/rollback`,
      )
      .then((r) => r.data),

  compare: (
    organizationId: string,
    projectId: string,
    promptId: string,
    from: number,
    to: number,
  ) =>
    apiClient
      .get<PromptCompareResponse>(`${base(organizationId, projectId)}/${promptId}/compare`, {
        params: { from, to },
      })
      .then((r) => r.data),
};
