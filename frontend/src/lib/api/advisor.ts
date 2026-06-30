import { apiClient } from "@/lib/api/client";
import type { Confidence, Severity } from "@/lib/format";

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------
export type AdvisoryScope = "PROJECT" | "AGENT" | "PROMPT";

export type RecommendationCategory =
  | "PROMPT"
  | "RAG"
  | "AGENT"
  | "MODEL"
  | "COST"
  | "RELIABILITY"
  | "QUALITY"
  | "LATENCY";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface SeverityBreakdownEntry {
  severity: Severity;
  count: number;
}

export interface RecommendationResponse {
  category: RecommendationCategory;
  title: string;
  why: string;
  whatChanged: string | null;
  howToFix: string;
  expectedImprovement: string | null;
  confidence: Confidence;
  severity: Severity;
  evidence: string[];
  knowledgeKey: string | null;
}

export interface AdvisoryReportResponse {
  scope: AdvisoryScope;
  subject: string;
  recommendationCount: number;
  severityBreakdown: SeverityBreakdownEntry[];
  recommendations: RecommendationResponse[];
  notes: string[];
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/advisor`;
}

export const advisorApi = {
  adviseProject: (organizationId: string, projectId: string) =>
    apiClient
      .get<AdvisoryReportResponse>(base(organizationId, projectId))
      .then((r) => r.data),

  adviseAgent: (organizationId: string, projectId: string, agentId: string) =>
    apiClient
      .get<AdvisoryReportResponse>(`${base(organizationId, projectId)}/agents/${agentId}`)
      .then((r) => r.data),

  advisePrompt: (
    organizationId: string,
    projectId: string,
    promptId: string,
    versionId?: string,
  ) =>
    apiClient
      .get<AdvisoryReportResponse>(`${base(organizationId, projectId)}/prompts/${promptId}`, {
        params: versionId ? { versionId } : undefined,
      })
      .then((r) => r.data),
};
