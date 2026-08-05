import { apiClient } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/types";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface PlatformHealthResponse {
  enabled: boolean;
  chainValid: boolean;
  integrityClean: boolean;
  integrityErrors: number;
  ledgerSize: number;
}

export type GraphNodeType =
  | "organization"
  | "project"
  | "provider"
  | "model"
  | "agent"
  | "prompt"
  | "dataset"
  | "evaluation";

export interface GraphNode {
  id: string;
  type: GraphNodeType | string;
  label: string;
  subtitle: string | null;
  entityId: string | null;
  projectId: string | null;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  relation: string;
}

export interface PlatformGraphResponse {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface RegistryItem {
  id: string;
  type: string;
  name: string;
  subtitle: string | null;
  entityId: string | null;
  projectId: string | null;
  projectName: string | null;
  providerId: string | null;
  tags: string[];
  createdAt: string;
}

export interface RegistryTypeCount {
  type: string;
  count: number;
}

export interface RegistryQuery {
  q?: string;
  type?: string;
  projectId?: string;
  providerId?: string;
  tag?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export interface EvolutionRef {
  id: string;
  type: string;
  name: string;
  entityId: string | null;
  projectId: string | null;
  relation: string | null;
}

export interface EvolutionRevision {
  label: string;
  detail: string | null;
  active: boolean;
  at: string;
}

export interface EvolutionEvidence {
  id: string;
  type: string;
  name: string;
  outcome: string | null;
  entityId: string | null;
  projectId: string | null;
  at: string;
}

export interface ArtifactEvolution {
  artifact: EvolutionRef;
  dependencies: EvolutionRef[];
  dependents: EvolutionRef[];
  impactCount: number;
  history: EvolutionRevision[];
  evidence: EvolutionEvidence[];
}

// ---- P11 · Engineering Intelligence ---------------------------------------
export type KnowledgeType = "observation" | "claim" | "decision" | "evidence" | "knowledge";

export interface KnowledgeLink {
  id: string;
  type: string;
  relation: string;
  label: string;
}

export interface KnowledgeObject {
  id: string;
  type: KnowledgeType | string;
  title: string;
  summary: string;
  rationale: string | null;
  artifactType: string;
  artifactEntityId: string | null;
  projectId: string | null;
  outcome: string | null;
  at: string;
  links: KnowledgeLink[];
}

export interface MemoryEntry {
  decisionId: string;
  question: string;
  answer: string;
  at: string;
}

export interface ArtifactIntelligence {
  artifact: EvolutionRef;
  observations: KnowledgeObject[];
  claims: KnowledgeObject[];
  decisions: KnowledgeObject[];
  evidence: KnowledgeObject[];
  knowledge: KnowledgeObject[];
  memory: MemoryEntry[];
}

export interface EngineeringRevision {
  id: string;
  artifactType: string;
  artifactEntityId: string | null;
  label: string;
  detail: string | null;
  rationale: string | null;
  active: boolean;
  rollbackReady: boolean;
  at: string;
  snapshot: Record<string, string | null>;
}

export interface EngineeringRevisionTimeline {
  artifact: EvolutionRef;
  revisions: EngineeringRevision[];
  promotions: number;
}

export interface RevisionDiff {
  field: string;
  before: string | null;
  after: string | null;
  change: "added" | "removed" | "changed" | "unchanged" | string;
}

export interface RevisionComparison {
  artifactType: string;
  artifactEntityId: string | null;
  base: EngineeringRevision;
  target: EngineeringRevision;
  diffs: RevisionDiff[];
}

export interface KnowledgeQuery {
  q?: string;
  type?: string;
  artifactType?: string;
  projectId?: string;
  sort?: string;
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------------
// Endpoints — read-only platform observability namespace ("/platform/*").
// ---------------------------------------------------------------------------
function base(organizationId: string) {
  return `/api/v1/organizations/${organizationId}/platform`;
}

export const platformApi = {
  health: (organizationId: string) =>
    apiClient.get<PlatformHealthResponse>(`${base(organizationId)}/health`).then((r) => r.data),
  graph: (organizationId: string, opts: { includeKnowledge?: boolean } = {}) =>
    apiClient
      .get<PlatformGraphResponse>(`${base(organizationId)}/graph`, {
        params: opts.includeKnowledge ? { include: "knowledge" } : undefined,
      })
      .then((r) => r.data),
  registry: (organizationId: string, query: RegistryQuery = {}) =>
    apiClient
      .get<PageResponse<RegistryItem>>(`${base(organizationId)}/registry`, { params: query })
      .then((r) => r.data),
  registryTypes: (organizationId: string) =>
    apiClient.get<RegistryTypeCount[]>(`${base(organizationId)}/registry/types`).then((r) => r.data),
  evolution: (organizationId: string, type: string, entityId: string) =>
    apiClient
      .get<ArtifactEvolution>(`${base(organizationId)}/evolution/${type}/${entityId}`)
      .then((r) => r.data),
  // ---- P11 · Engineering Intelligence ----
  knowledge: (organizationId: string, query: KnowledgeQuery = {}) =>
    apiClient
      .get<PageResponse<KnowledgeObject>>(`${base(organizationId)}/knowledge`, { params: query })
      .then((r) => r.data),
  knowledgeObject: (organizationId: string, kind: KnowledgeType, id: string) =>
    apiClient
      .get<KnowledgeObject>(`${base(organizationId)}/${kind}/${encodeURIComponent(id)}`)
      .then((r) => r.data),
  intelligence: (organizationId: string, type: string, entityId: string) =>
    apiClient
      .get<ArtifactIntelligence>(`${base(organizationId)}/intelligence/${type}/${entityId}`)
      .then((r) => r.data),
  revisions: (organizationId: string, type: string, entityId: string) =>
    apiClient
      .get<EngineeringRevisionTimeline>(`${base(organizationId)}/revisions/${type}/${entityId}`)
      .then((r) => r.data),
  compare: (organizationId: string, type: string, entityId: string, baseId: string, targetId: string) =>
    apiClient
      .get<RevisionComparison>(`${base(organizationId)}/compare`, {
        params: { type, entityId, base: baseId, target: targetId },
      })
      .then((r) => r.data),
};
