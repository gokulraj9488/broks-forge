import { apiClient } from "@/lib/api/client";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface AnalyticsTrendPoint {
  date: string;
  runCount: number;
  avgLatencyMs: number;
  totalTokens: number;
  totalCost: number;
}

export interface AnalyticsResponse {
  windowDays: number;
  jobCount: number;
  runCount: number;
  passRate: number;
  avgLatencyMs: number;
  totalTokens: number;
  totalCost: number;
  trend: AnalyticsTrendPoint[];
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/analytics`;
}

export const analyticsApi = {
  get: (organizationId: string, projectId: string, windowDays = 30) =>
    apiClient
      .get<AnalyticsResponse>(base(organizationId, projectId), { params: { windowDays } })
      .then((r) => r.data),
};
