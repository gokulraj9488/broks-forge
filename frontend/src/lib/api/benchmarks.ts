import { apiClient } from "@/lib/api/client";
import type { PageParams } from "@/lib/api/organizations";
import type { PageResponse } from "@/lib/api/types";
import type { EvaluationJobStatus, EvaluationJobSummaryStats } from "@/lib/api/evaluation-jobs";

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------
export type BenchmarkType =
  | "AGENT_VS_AGENT"
  | "VERSION_VS_VERSION"
  | "PROMPT_VS_PROMPT"
  | "MODEL_VS_MODEL"
  | "DATASET_VS_DATASET"
  | "PROFILE_VS_PROFILE";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface BenchmarkSummaryResponse {
  id: string;
  name: string;
  type: BenchmarkType;
  metricKey: string | null;
  entryCount: number;
  createdAt: string;
}

export interface BenchmarkEntry {
  id: string;
  evaluationJobId: string;
  label: string | null;
}

export interface BenchmarkResponse {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  description: string | null;
  type: BenchmarkType;
  metricKey: string | null;
  entries: BenchmarkEntry[];
  createdAt: string;
}

export interface LeaderboardRanking {
  rank: number;
  label: string;
  evaluationJobId: string;
  agentId: string | null;
  jobStatus: EvaluationJobStatus;
  score: number | null;
  summary: EvaluationJobSummaryStats | null;
}

export interface BenchmarkLeaderboardResponse {
  metricKey: string;
  higherIsBetter: boolean;
  rankings: LeaderboardRanking[];
}

// ---------------------------------------------------------------------------
// Request payloads
// ---------------------------------------------------------------------------
export interface CreateBenchmarkEntryInput {
  evaluationJobId: string;
  label?: string;
}

export interface CreateBenchmarkPayload {
  name: string;
  description?: string;
  type: BenchmarkType;
  metricKey?: string;
  entries: CreateBenchmarkEntryInput[];
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/benchmarks`;
}

export const benchmarksApi = {
  list: (organizationId: string, projectId: string, params: PageParams = {}) =>
    apiClient
      .get<PageResponse<BenchmarkSummaryResponse>>(base(organizationId, projectId), { params })
      .then((r) => r.data),

  get: (organizationId: string, projectId: string, benchmarkId: string) =>
    apiClient
      .get<BenchmarkResponse>(`${base(organizationId, projectId)}/${benchmarkId}`)
      .then((r) => r.data),

  create: (organizationId: string, projectId: string, payload: CreateBenchmarkPayload) =>
    apiClient
      .post<BenchmarkResponse>(base(organizationId, projectId), payload)
      .then((r) => r.data),

  remove: (organizationId: string, projectId: string, benchmarkId: string) =>
    apiClient
      .delete<void>(`${base(organizationId, projectId)}/${benchmarkId}`)
      .then((r) => r.data),

  leaderboard: (organizationId: string, projectId: string, benchmarkId: string) =>
    apiClient
      .get<BenchmarkLeaderboardResponse>(
        `${base(organizationId, projectId)}/${benchmarkId}/leaderboard`,
      )
      .then((r) => r.data),

  addEntry: (
    organizationId: string,
    projectId: string,
    benchmarkId: string,
    payload: CreateBenchmarkEntryInput,
  ) =>
    apiClient
      .post<BenchmarkEntry>(
        `${base(organizationId, projectId)}/${benchmarkId}/entries`,
        payload,
      )
      .then((r) => r.data),

  removeEntry: (
    organizationId: string,
    projectId: string,
    benchmarkId: string,
    entryId: string,
  ) =>
    apiClient
      .delete<void>(`${base(organizationId, projectId)}/${benchmarkId}/entries/${entryId}`)
      .then((r) => r.data),
};

// ---------------------------------------------------------------------------
// UI option lists
// ---------------------------------------------------------------------------
export const BENCHMARK_TYPE_OPTIONS: { value: BenchmarkType; label: string }[] = [
  { value: "AGENT_VS_AGENT", label: "Agent vs Agent" },
  { value: "VERSION_VS_VERSION", label: "Version vs Version" },
  { value: "PROMPT_VS_PROMPT", label: "Prompt vs Prompt" },
  { value: "MODEL_VS_MODEL", label: "Model vs Model" },
  { value: "DATASET_VS_DATASET", label: "Dataset vs Dataset" },
  { value: "PROFILE_VS_PROFILE", label: "Profile vs Profile" },
];

export const BENCHMARK_TYPE_LABELS = Object.fromEntries(
  BENCHMARK_TYPE_OPTIONS.map((o) => [o.value, o.label]),
) as Record<BenchmarkType, string>;

export const METRIC_KEY_OPTIONS: { value: string; label: string }[] = [
  { value: "passRate", label: "Pass rate" },
  { value: "avgScore", label: "Average score" },
  { value: "avgLatencyMs", label: "Average latency" },
  { value: "totalCost", label: "Total cost" },
  { value: "totalTokens", label: "Total tokens" },
];
