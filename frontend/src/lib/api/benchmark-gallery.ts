import { apiClient } from "@/lib/api/client";
import type { EvaluationMetric } from "@/lib/api/evaluation-profiles";
import type { EvaluationJobResponse } from "@/lib/api/evaluation-jobs";

export type GalleryTemplateKey =
  | "CUSTOMER_SUPPORT"
  | "RAG"
  | "CODING"
  | "REASONING"
  | "HALLUCINATION"
  | "SAFETY"
  | "SUMMARIZATION"
  | "TRANSLATION";

export interface GalleryTemplateResponse {
  key: GalleryTemplateKey;
  name: string;
  description: string;
  category: string;
  datasetItemCount: number;
  metrics: EvaluationMetric[];
  requiresJudgeProvider: boolean;
  requiresEmbeddingProvider: boolean;
}

export interface ProvisionGalleryBenchmarkPayload {
  templateKey: GalleryTemplateKey;
  agentId: string;
  judgeProviderId?: string;
  judgeModel?: string;
  embeddingProviderId?: string;
  embeddingModel?: string;
  name?: string;
}

export interface ProvisionGalleryBenchmarkResponse {
  datasetId: string;
  promptId: string;
  profileId: string;
  job: EvaluationJobResponse;
}

function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/benchmark-gallery`;
}

export const benchmarkGalleryApi = {
  listTemplates: (organizationId: string, projectId: string) =>
    apiClient
      .get<GalleryTemplateResponse[]>(`${base(organizationId, projectId)}/templates`)
      .then((r) => r.data),

  provision: (organizationId: string, projectId: string, payload: ProvisionGalleryBenchmarkPayload) =>
    apiClient
      .post<ProvisionGalleryBenchmarkResponse>(`${base(organizationId, projectId)}/provision`, payload)
      .then((r) => r.data),
};
