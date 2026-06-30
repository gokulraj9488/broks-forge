import { apiClient } from "@/lib/api/client";
import type { EvaluationRunStatus } from "@/lib/api/evaluation-jobs";

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------
export type TimelineStage =
  | "PROMPT"
  | "MEMORY"
  | "RETRIEVER"
  | "TOOLS"
  | "MODEL"
  | "PARSER"
  | "OUTPUT";

export type TimelineStageStatus =
  | "OK"
  | "WARN"
  | "ERROR"
  | "SKIPPED"
  | "NOT_INSTRUMENTED";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface TimelineStageResponse {
  stage: TimelineStage;
  label: string;
  status: TimelineStageStatus;
  startOffsetMs: number | null;
  durationMs: number | null;
  detail: string | null;
  explanation: string | null;
}

export interface ExecutionTimelineResponse {
  jobId: string;
  runId: string;
  sequence: number;
  runStatus: EvaluationRunStatus;
  passed: boolean | null;
  totalLatencyMs: number | null;
  provider: string | null;
  model: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  cost: number | null;
  stages: TimelineStageResponse[];
  failureExplanation: string | null;
  tracingActive: boolean;
  notes: string[];
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/debugger`;
}

export const debuggerApi = {
  timeline: (
    organizationId: string,
    projectId: string,
    jobId: string,
    runId: string,
  ) =>
    apiClient
      .get<ExecutionTimelineResponse>(
        `${base(organizationId, projectId)}/jobs/${jobId}/runs/${runId}/timeline`,
      )
      .then((r) => r.data),
};
