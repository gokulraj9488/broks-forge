import { apiClient } from "@/lib/api/client";
import type { Confidence, Severity } from "@/lib/format";

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------
export type RootCauseScope = "JOB" | "REGRESSION";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface RootCauseFindingResponse {
  rootCause: string;
  severity: Severity;
  confidence: Confidence;
  evidence: string[];
  recommendation: string;
  expectedImprovement: string;
  knowledgeKey: string | null;
}

export interface RootCauseReportResponse {
  scope: RootCauseScope;
  subject: string;
  findingCount: number;
  findings: RootCauseFindingResponse[];
  notes: string[];
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/root-cause`;
}

export const rootCauseApi = {
  forJob: (organizationId: string, projectId: string, jobId: string) =>
    apiClient
      .get<RootCauseReportResponse>(`${base(organizationId, projectId)}/jobs/${jobId}`)
      .then((r) => r.data),

  forRegression: (organizationId: string, projectId: string, checkId: string) =>
    apiClient
      .get<RootCauseReportResponse>(`${base(organizationId, projectId)}/regressions/${checkId}`)
      .then((r) => r.data),
};
