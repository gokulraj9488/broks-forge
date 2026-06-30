import { apiClient } from "@/lib/api/client";
import type { PageParams } from "@/lib/api/organizations";
import type { PageResponse } from "@/lib/api/types";
import type { LlmProvider } from "@/lib/api/agents";
import type { MetricType } from "@/lib/api/evaluation-profiles";

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------
export type EvaluationJobStatus =
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export type EvaluationRunStatus =
  | "PENDING"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "SKIPPED";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface EvaluationJobSummaryResponse {
  id: string;
  name: string;
  status: EvaluationJobStatus;
  agentId: string;
  datasetId: string;
  totalItems: number;
  completedItems: number;
  failedItems: number;
  createdAt: string;
  completedAt: string | null;
}

export interface EvaluationJobSummaryStats {
  totalRuns: number;
  succeeded: number;
  failed: number;
  passed: number;
  passRate: number;
  avgLatencyMs: number;
  totalTokens: number;
  totalCost: number;
  avgScore: number;
  metricPassRates: Record<string, number>;
}

export interface EvaluationJobResponse {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  status: EvaluationJobStatus;
  agentId: string;
  agentVersionId: string | null;
  datasetId: string;
  datasetVersionId: string | null;
  promptId: string | null;
  promptVersionId: string | null;
  profileId: string | null;
  provider: LlmProvider | null;
  model: string | null;
  parameters: Record<string, unknown> | null;
  totalItems: number;
  completedItems: number;
  failedItems: number;
  summary: EvaluationJobSummaryStats | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface EvaluationRunResponse {
  id: string;
  sequence: number;
  status: EvaluationRunStatus;
  input: string;
  output: string | null;
  latencyMs: number | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  cost: number | null;
  httpStatus: number | null;
  passed: boolean | null;
  score: number | null;
  error: string | null;
  completedAt: string | null;
}

export interface EvaluationRunResultResponse {
  metricType: MetricType;
  metricLabel: string | null;
  passed: boolean;
  score: number | null;
  threshold: number | null;
  detail: string | null;
}

// ---------------------------------------------------------------------------
// Request payloads
// ---------------------------------------------------------------------------
export interface CreateEvaluationJobPayload {
  name: string;
  agentId: string;
  agentVersionId?: string;
  datasetId: string;
  datasetVersionId?: string;
  promptId?: string;
  promptVersionId?: string;
  profileId?: string;
  provider?: LlmProvider;
  model?: string;
  parameters?: Record<string, unknown>;
  autoRun?: boolean;
}

export interface EvaluationJobFilterParams extends PageParams {
  q?: string;
  status?: EvaluationJobStatus;
  agentId?: string;
  datasetId?: string;
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/evaluation-jobs`;
}

export const evaluationJobsApi = {
  list: (
    organizationId: string,
    projectId: string,
    params: EvaluationJobFilterParams = {},
  ) =>
    apiClient
      .get<PageResponse<EvaluationJobSummaryResponse>>(base(organizationId, projectId), {
        params,
      })
      .then((r) => r.data),

  get: (organizationId: string, projectId: string, jobId: string) =>
    apiClient
      .get<EvaluationJobResponse>(`${base(organizationId, projectId)}/${jobId}`)
      .then((r) => r.data),

  create: (
    organizationId: string,
    projectId: string,
    payload: CreateEvaluationJobPayload,
  ) =>
    apiClient
      .post<EvaluationJobResponse>(base(organizationId, projectId), payload)
      .then((r) => r.data),

  remove: (organizationId: string, projectId: string, jobId: string) =>
    apiClient
      .delete<void>(`${base(organizationId, projectId)}/${jobId}`)
      .then((r) => r.data),

  run: (organizationId: string, projectId: string, jobId: string) =>
    apiClient
      .post<EvaluationJobResponse>(`${base(organizationId, projectId)}/${jobId}/run`)
      .then((r) => r.data),

  cancel: (organizationId: string, projectId: string, jobId: string) =>
    apiClient
      .post<EvaluationJobResponse>(`${base(organizationId, projectId)}/${jobId}/cancel`)
      .then((r) => r.data),

  listRuns: (
    organizationId: string,
    projectId: string,
    jobId: string,
    params: PageParams = {},
  ) =>
    apiClient
      .get<PageResponse<EvaluationRunResponse>>(
        `${base(organizationId, projectId)}/${jobId}/runs`,
        { params },
      )
      .then((r) => r.data),

  runResults: (
    organizationId: string,
    projectId: string,
    jobId: string,
    runId: string,
  ) =>
    apiClient
      .get<EvaluationRunResultResponse[]>(
        `${base(organizationId, projectId)}/${jobId}/runs/${runId}/results`,
      )
      .then((r) => r.data),
};

// ---------------------------------------------------------------------------
// UI option lists
// ---------------------------------------------------------------------------
export const JOB_STATUS_OPTIONS: { value: EvaluationJobStatus; label: string }[] = [
  { value: "PENDING", label: "Pending" },
  { value: "RUNNING", label: "Running" },
  { value: "COMPLETED", label: "Completed" },
  { value: "FAILED", label: "Failed" },
  { value: "CANCELLED", label: "Cancelled" },
];
