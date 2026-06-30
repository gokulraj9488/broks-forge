import { apiClient } from "@/lib/api/client";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export type SearchHitType =
  | "AGENT"
  | "DATASET"
  | "PROMPT"
  | "EVALUATION_JOB"
  | "BENCHMARK"
  | "EVALUATION_PROFILE";

export interface SearchHit {
  type: SearchHitType;
  id: string;
  title: string;
  subtitle: string | null;
}

export interface SearchResponse {
  query: string;
  hits: SearchHit[];
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/search`;
}

export const searchApi = {
  query: (organizationId: string, projectId: string, q: string, limit = 5) =>
    apiClient
      .get<SearchResponse>(base(organizationId, projectId), { params: { q, limit } })
      .then((r) => r.data),
};
