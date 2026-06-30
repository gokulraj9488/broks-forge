import { apiClient } from "@/lib/api/client";
import type { PageParams } from "@/lib/api/organizations";
import type { PageResponse } from "@/lib/api/types";

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------
export type MetricType =
  | "EXACT_MATCH"
  | "CONTAINS"
  | "REGEX_MATCH"
  | "JSON_VALID"
  | "NON_EMPTY"
  | "LENGTH"
  | "LATENCY"
  | "COST"
  | "TOKEN_COUNT";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface EvaluationMetric {
  type: MetricType;
  label?: string | null;
  weight?: number | null;
  threshold?: number | null;
  params?: Record<string, unknown> | null;
}

export interface EvaluationProfileSummaryResponse {
  id: string;
  name: string;
  slug: string;
  metricCount: number;
  passThreshold: number | null;
  updatedAt: string;
}

export interface EvaluationProfileResponse {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  slug: string;
  description: string | null;
  metrics: EvaluationMetric[];
  passThreshold: number | null;
  createdAt: string;
  updatedAt: string;
}

// ---------------------------------------------------------------------------
// Request payloads
// ---------------------------------------------------------------------------
export interface CreateEvaluationProfilePayload {
  name: string;
  slug?: string;
  description?: string;
  metrics: EvaluationMetric[];
  passThreshold?: number;
}

export type UpdateEvaluationProfilePayload = Partial<CreateEvaluationProfilePayload>;

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/evaluation-profiles`;
}

export const evaluationProfilesApi = {
  list: (organizationId: string, projectId: string, params: PageParams = {}) =>
    apiClient
      .get<PageResponse<EvaluationProfileSummaryResponse>>(base(organizationId, projectId), {
        params,
      })
      .then((r) => r.data),

  get: (organizationId: string, projectId: string, profileId: string) =>
    apiClient
      .get<EvaluationProfileResponse>(`${base(organizationId, projectId)}/${profileId}`)
      .then((r) => r.data),

  create: (
    organizationId: string,
    projectId: string,
    payload: CreateEvaluationProfilePayload,
  ) =>
    apiClient
      .post<EvaluationProfileResponse>(base(organizationId, projectId), payload)
      .then((r) => r.data),

  update: (
    organizationId: string,
    projectId: string,
    profileId: string,
    payload: UpdateEvaluationProfilePayload,
  ) =>
    apiClient
      .patch<EvaluationProfileResponse>(
        `${base(organizationId, projectId)}/${profileId}`,
        payload,
      )
      .then((r) => r.data),

  remove: (organizationId: string, projectId: string, profileId: string) =>
    apiClient
      .delete<void>(`${base(organizationId, projectId)}/${profileId}`)
      .then((r) => r.data),
};

// ---------------------------------------------------------------------------
// UI option lists
// ---------------------------------------------------------------------------
export const METRIC_TYPE_OPTIONS: {
  value: MetricType;
  label: string;
  description: string;
}[] = [
  { value: "EXACT_MATCH", label: "Exact match", description: "Output equals the expected output." },
  { value: "CONTAINS", label: "Contains", description: "Output contains the expected substring." },
  { value: "REGEX_MATCH", label: "Regex match", description: "Output matches a regular expression." },
  { value: "JSON_VALID", label: "JSON valid", description: "Output parses as valid JSON." },
  { value: "NON_EMPTY", label: "Non-empty", description: "Output is not blank." },
  { value: "LENGTH", label: "Length", description: "Output length within bounds." },
  { value: "LATENCY", label: "Latency", description: "Response time under a threshold." },
  { value: "COST", label: "Cost", description: "Run cost under a threshold." },
  { value: "TOKEN_COUNT", label: "Token count", description: "Token usage under a threshold." },
];

export const METRIC_TYPE_LABELS = Object.fromEntries(
  METRIC_TYPE_OPTIONS.map((o) => [o.value, o.label]),
) as Record<MetricType, string>;
