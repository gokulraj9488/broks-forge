import { apiClient } from "@/lib/api/client";
import type { PageParams } from "@/lib/api/organizations";
import type { ApiKeyResponse, CreatedApiKeyResponse, PageResponse } from "@/lib/api/types";

export interface CreateApiKeyPayload {
  name: string;
  expiresInDays?: number;
}

export const apiKeysApi = {
  list: (organizationId: string, projectId: string, params: PageParams = {}) =>
    apiClient
      .get<PageResponse<ApiKeyResponse>>(
        `/api/v1/organizations/${organizationId}/projects/${projectId}/api-keys`,
        { params },
      )
      .then((r) => r.data),

  create: (organizationId: string, projectId: string, payload: CreateApiKeyPayload) =>
    apiClient
      .post<CreatedApiKeyResponse>(
        `/api/v1/organizations/${organizationId}/projects/${projectId}/api-keys`,
        payload,
      )
      .then((r) => r.data),

  revoke: (organizationId: string, projectId: string, apiKeyId: string) =>
    apiClient
      .delete<void>(
        `/api/v1/organizations/${organizationId}/projects/${projectId}/api-keys/${apiKeyId}`,
      )
      .then((r) => r.data),
};
