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
  | "SEMANTIC_SIMILARITY"
  | "LLM_JUDGE"
  | "HALLUCINATION_DETECTION"
  | "CITATION_VERIFICATION"
  | "CUSTOM"
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

export interface EvaluationProfileResponse {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  slug: string;
  description: string | null;
  metrics: EvaluationMetric[];
  passThreshold: number | null;
  enabled: boolean;
  currentVersionNumber: number;
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

export interface ListEvaluationProfilesParams extends PageParams {
  search?: string;
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/evaluation-profiles`;
}

export const evaluationProfilesApi = {
  list: (organizationId: string, projectId: string, params: ListEvaluationProfilesParams = {}) =>
    apiClient
      .get<PageResponse<EvaluationProfileResponse>>(base(organizationId, projectId), {
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

  duplicate: (organizationId: string, projectId: string, profileId: string) =>
    apiClient
      .post<EvaluationProfileResponse>(`${base(organizationId, projectId)}/${profileId}/duplicate`)
      .then((r) => r.data),

  enable: (organizationId: string, projectId: string, profileId: string) =>
    apiClient
      .post<EvaluationProfileResponse>(`${base(organizationId, projectId)}/${profileId}/enable`)
      .then((r) => r.data),

  disable: (organizationId: string, projectId: string, profileId: string) =>
    apiClient
      .post<EvaluationProfileResponse>(`${base(organizationId, projectId)}/${profileId}/disable`)
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
  { value: "JSON_VALID", label: "JSON valid", description: "Output parses as valid JSON, optionally against a JSON Schema." },
  { value: "NON_EMPTY", label: "Non-empty", description: "Output is not blank." },
  { value: "LENGTH", label: "Length", description: "Output length within bounds." },
  {
    value: "SEMANTIC_SIMILARITY",
    label: "Semantic similarity",
    description: "Embedding similarity to the expected output — for conversational answers where wording varies.",
  },
  {
    value: "LLM_JUDGE",
    label: "LLM judge",
    description: "A judge model scores overall quality against a rubric.",
  },
  {
    value: "HALLUCINATION_DETECTION",
    label: "Hallucination detection",
    description: "A judge model flags claims unsupported by the provided context.",
  },
  {
    value: "CITATION_VERIFICATION",
    label: "Citation verification",
    description: "A judge model checks citations are consistent with the provided context.",
  },
  {
    value: "CUSTOM",
    label: "Custom",
    description: "Dispatches to a named custom metric evaluator registered on the backend.",
  },
  { value: "LATENCY", label: "Latency", description: "Response time under a threshold." },
  { value: "COST", label: "Cost", description: "Run cost under a threshold." },
  { value: "TOKEN_COUNT", label: "Token count", description: "Token usage under a threshold." },
];

export const METRIC_TYPE_LABELS = Object.fromEntries(
  METRIC_TYPE_OPTIONS.map((o) => [o.value, o.label]),
) as Record<MetricType, string>;

/** Metric types whose params require selecting a Provider (judge/embedding call). */
export const JUDGE_FAMILY_METRIC_TYPES: MetricType[] = [
  "SEMANTIC_SIMILARITY",
  "LLM_JUDGE",
  "HALLUCINATION_DETECTION",
  "CITATION_VERIFICATION",
];

// ---------------------------------------------------------------------------
// Built-in presets — frontend-only starting points for the profile editor.
// Fully editable after creation; not a backend concept.
// ---------------------------------------------------------------------------
export type ProfilePreset = "EXACT_MATCH" | "CONVERSATION" | "RAG" | "JSON_API";

export const PRESET_OPTIONS: { value: ProfilePreset; label: string; description: string }[] = [
  { value: "EXACT_MATCH", label: "Exact match", description: "Exact Match + Non-empty — deterministic, structured outputs." },
  { value: "CONVERSATION", label: "Conversation", description: "Non-empty + Semantic Similarity + LLM Judge — conversational agents where wording varies." },
  { value: "RAG", label: "RAG", description: "Semantic Similarity + Citation Verification + Hallucination Detection — retrieval-augmented agents." },
  { value: "JSON_API", label: "JSON API", description: "JSON Schema Validation — structured/tool-calling agents." },
];

export function presetMetrics(preset: ProfilePreset): MetricType[] {
  switch (preset) {
    case "EXACT_MATCH":
      return ["EXACT_MATCH", "NON_EMPTY"];
    case "CONVERSATION":
      return ["NON_EMPTY", "SEMANTIC_SIMILARITY", "LLM_JUDGE"];
    case "RAG":
      return ["SEMANTIC_SIMILARITY", "CITATION_VERIFICATION", "HALLUCINATION_DETECTION"];
    case "JSON_API":
      return ["JSON_VALID"];
  }
}
