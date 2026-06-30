import { apiClient } from "@/lib/api/client";

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------
export type KnowledgeNodeType =
  | "FAILURE_MODE"
  | "REGRESSION"
  | "RECOMMENDATION"
  | "OPTIMIZATION";

export type KnowledgeRelation = "CAUSES" | "MITIGATED_BY" | "LEADS_TO" | "RELATED_TO";

export type KnowledgeDirection = "OUTGOING" | "INCOMING";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface KnowledgeNodeResponse {
  nodeKey: string;
  nodeType: KnowledgeNodeType;
  title: string;
  category: string;
  summary: string;
  detectionHint: string;
  remediation: string;
  expectedImprovement: string;
  defaultSeverity: string;
  defaultConfidence: string;
  tags: string[];
  occurrenceCount: number;
}

export interface KnowledgeEdgeResponse {
  sourceNodeKey: string;
  targetNodeKey: string;
  relation: KnowledgeRelation;
  weight: number;
}

export interface KnowledgeGraphResponse {
  nodes: KnowledgeNodeResponse[];
  edges: KnowledgeEdgeResponse[];
}

export interface KnowledgeNeighborResponse {
  relation: KnowledgeRelation;
  direction: KnowledgeDirection;
  node: KnowledgeNodeResponse;
}

export interface KnowledgeNodeDetailResponse {
  node: KnowledgeNodeResponse;
  neighbors: KnowledgeNeighborResponse[];
}

export interface KnowledgeNodeFilterParams {
  type?: KnowledgeNodeType;
  category?: string;
}

// ---------------------------------------------------------------------------
// Endpoints — NOT project-scoped.
// ---------------------------------------------------------------------------
const BASE = "/api/v1/knowledge";

export const knowledgeApi = {
  listNodes: (params: KnowledgeNodeFilterParams = {}) =>
    apiClient
      .get<KnowledgeNodeResponse[]>(`${BASE}/nodes`, { params })
      .then((r) => r.data),

  getNode: (nodeKey: string) =>
    apiClient
      .get<KnowledgeNodeDetailResponse>(`${BASE}/nodes/${nodeKey}`)
      .then((r) => r.data),

  graph: () => apiClient.get<KnowledgeGraphResponse>(`${BASE}/graph`).then((r) => r.data),
};
