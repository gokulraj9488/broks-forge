import { apiClient } from "@/lib/api/client";
import type { PageParams } from "@/lib/api/organizations";
import type { PageResponse } from "@/lib/api/types";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface RegressionCheckSummaryResponse {
  id: string;
  name: string;
  baselineJobId: string;
  candidateJobId: string;
  regressed: boolean;
  createdAt: string;
}

export interface RegressionFinding {
  label: string;
  // Null when the metric wasn't reported by one of the two jobs (e.g. no score metric
  // configured, or all runs failed before latency capture) — never assume non-null.
  baseline: number | null;
  candidate: number | null;
  deltaPct: number | null;
  regressed: boolean;
  lowerIsBetter: boolean;
}

export interface RegressionCheckResponse {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  baselineJobId: string;
  candidateJobId: string;
  tolerancePct: number;
  regressed: boolean;
  findings: Record<string, RegressionFinding>;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// Request payloads
// ---------------------------------------------------------------------------
export interface CreateRegressionCheckPayload {
  name: string;
  baselineJobId: string;
  candidateJobId: string;
  tolerancePct?: number;
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/regression-checks`;
}

export const regressionApi = {
  list: (organizationId: string, projectId: string, params: PageParams = {}) =>
    apiClient
      .get<PageResponse<RegressionCheckSummaryResponse>>(base(organizationId, projectId), {
        params,
      })
      .then((r) => r.data),

  get: (organizationId: string, projectId: string, checkId: string) =>
    apiClient
      .get<RegressionCheckResponse>(`${base(organizationId, projectId)}/${checkId}`)
      .then((r) => r.data),

  create: (
    organizationId: string,
    projectId: string,
    payload: CreateRegressionCheckPayload,
  ) =>
    apiClient
      .post<RegressionCheckResponse>(base(organizationId, projectId), payload)
      .then((r) => r.data),

  remove: (organizationId: string, projectId: string, checkId: string) =>
    apiClient
      .delete<void>(`${base(organizationId, projectId)}/${checkId}`)
      .then((r) => r.data),
};
