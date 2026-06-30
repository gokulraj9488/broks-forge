import { apiClient } from "@/lib/api/client";
import type { EvaluationJobSummaryResponse } from "@/lib/api/evaluation-jobs";
import type { AnalyticsResponse } from "@/lib/api/analytics";
import type { ReportResponse } from "@/lib/api/reports";
import type { RegressionCheckSummaryResponse } from "@/lib/api/regression";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface DashboardCounts {
  agents: number;
  datasets: number;
  prompts: number;
  evaluationJobs: number;
  runningJobs: number;
  benchmarks: number;
}

export interface TopAgentEntry {
  agentId: string;
  avgPassRate: number;
  evaluatedJobs: number;
}

export interface DashboardResponse {
  counts: DashboardCounts;
  recentJobs: EvaluationJobSummaryResponse[];
  analytics: AnalyticsResponse;
  recentReports: ReportResponse[];
  regressionAlerts: RegressionCheckSummaryResponse[];
  topAgents: TopAgentEntry[];
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/dashboard`;
}

export const dashboardApi = {
  get: (organizationId: string, projectId: string) =>
    apiClient.get<DashboardResponse>(base(organizationId, projectId)).then((r) => r.data),
};
