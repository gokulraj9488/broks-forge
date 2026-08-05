import { apiClient } from "@/lib/api/client";

/**
 * Brok (P12) — the Engineering Partner.
 *
 * The wire contract is deliberately not a chat contract. An answer is a verdict, a reasoning chain in which
 * every step declares how it is known, the records it was read from, the artifacts/knowledge/decisions/
 * evaluations/revisions it touched, recommendations that each carry a next action into an existing workflow,
 * and the follow-up investigations that continue the work.
 */

export type EpistemicStatusWire = "derived" | "inferred" | "suggested" | "unknown";
export type VerdictStateWire = "healthy" | "attention" | "risk" | "failed" | "unknown";
export type ConfidenceWire = "consistent-with" | "likely" | "near-certain";

/** A pointer at something that really exists in the engineering record. */
export interface BrokRef {
  id: string;
  type: string;
  label: string;
  detail: string | null;
  outcome: string | null;
  entityId: string | null;
  projectId: string | null;
  at: string | null;
}

export interface BrokStatement {
  text: string;
  status: EpistemicStatusWire;
  basis: string;
}

/** Every action names a surface that already exists; the client resolves it to that surface's real route. */
export type BrokActionKind =
  | "openGraph"
  | "openExecutionGraph"
  | "openFailureGraph"
  | "openIntelligence"
  | "openEvolution"
  | "openRevisions"
  | "compareRevisions"
  | "openKnowledge"
  | "openRegistry"
  | "openEvaluation"
  | "openAnalytics"
  | "openInsights"
  | "startInvestigation";

export interface BrokAction {
  kind: BrokActionKind | string;
  label: string;
  targetType: string | null;
  entityId: string | null;
  projectId: string | null;
  targetId: string | null;
  /** For startInvestigation: the question the new investigation opens with. */
  question: string | null;
}

export interface BrokRecommendation {
  title: string;
  why: string;
  impact: string;
  confidence: ConfidenceWire;
  status: EpistemicStatusWire;
  evidenceIds: string[];
  action: BrokAction | null;
}

export interface BrokFollowUp {
  question: string;
  rationale: string;
  focus: string | null;
}

export interface BrokVerdict {
  state: VerdictStateWire;
  headline: string;
  consequence: string | null;
  status: EpistemicStatusWire;
  confidence: ConfidenceWire;
  basis: string;
}

export interface BrokImpact {
  statement: string;
  count: number;
}

export interface BrokContext {
  organizationId: string;
  projectId: string | null;
  projectName: string | null;
  focus: BrokRef | null;
  scope: string;
  graphNodeIds: string[];
}

export interface BrokReferences {
  artifacts: BrokRef[];
  knowledge: BrokRef[];
  decisions: BrokRef[];
  evaluations: BrokRef[];
  revisions: BrokRef[];
}

export interface BrokAnswer {
  id: string;
  question: string;
  intent: string;
  kind: "question" | "brief" | string;
  verdict: BrokVerdict;
  reasoning: BrokStatement[];
  impact: BrokImpact;
  evidence: BrokRef[];
  references: BrokReferences;
  recommendations: BrokRecommendation[];
  memory: BrokMemory[];
  followUps: BrokFollowUp[];
  context: BrokContext;
  at: string;
}

/** A remembered engineering "why", carried unchanged from Engineering Memory. */
export interface BrokMemory {
  decisionId: string;
  question: string;
  answer: string;
  at: string;
}

export interface BrokBriefRef {
  kind: string;
  title: string;
  summary: string;
  available: boolean;
}

/** One earlier turn, sent so a follow-up can inherit its subject without the engineer restating it. */
export interface BrokTurn {
  question: string;
  intent: string;
  focus: string | null;
}

export interface BrokAskRequest {
  question: string;
  projectId?: string | null;
  focus?: string | null;
  history?: BrokTurn[];
}

function base(organizationId: string) {
  return `/api/v1/organizations/${organizationId}/brok`;
}

export const brokApi = {
  /** Read-only despite the POST: the question and its engineering context are a body, not a mutation. */
  ask: (organizationId: string, request: BrokAskRequest) =>
    apiClient.post<BrokAnswer>(`${base(organizationId)}/ask`, request).then((r) => r.data),
  suggestions: (organizationId: string, params: { projectId?: string; focus?: string } = {}) =>
    apiClient
      .get<BrokFollowUp[]>(`${base(organizationId)}/suggestions`, { params })
      .then((r) => r.data),
  context: (organizationId: string, params: { projectId?: string; focus?: string } = {}) =>
    apiClient.get<BrokContext>(`${base(organizationId)}/context`, { params }).then((r) => r.data),
  briefs: (organizationId: string, params: { projectId?: string } = {}) =>
    apiClient.get<BrokBriefRef[]>(`${base(organizationId)}/briefs`, { params }).then((r) => r.data),
  brief: (organizationId: string, kind: string, params: { projectId?: string } = {}) =>
    apiClient
      .get<BrokAnswer>(`${base(organizationId)}/brief/${kind}`, { params })
      .then((r) => r.data),
};
