import { apiClient } from "@/lib/api/client";
import type {
  BrokAction,
  BrokContext,
  BrokFollowUp,
  BrokImpact,
  BrokMemory,
  BrokRecommendation,
  BrokRef,
  BrokVerdict,
  ConfidenceWire,
  EpistemicStatusWire,
  VerdictStateWire,
} from "@/lib/api/brok";

/**
 * The Root Cause Explorer (P13) — one assembled engineering investigation.
 *
 * The types Brok already defines are imported rather than restated. That is not laziness: an investigation
 * and a Brok answer are two readings of the same engineering record, and giving them one vocabulary is what
 * lets the same components render both and guarantees the two surfaces can never disagree about what a
 * verdict, a confidence or a next action means.
 */

/** Where a cause sits in the causal chain. Stopping at "immediate" is what makes an error viewer. */
export type CauseLayer = "immediate" | "contributing" | "historical" | "related-change";

export type InvestigationEventKind =
  | "promotion"
  | "revision"
  | "dataset"
  | "evaluation"
  | "run"
  | "knowledge"
  | "decision"
  | "precedent";

export interface InvestigationEvent {
  id: string;
  at: string;
  kind: InvestigationEventKind | string;
  title: string;
  detail: string | null;
  state: VerdictStateWire;
  ref: BrokRef | null;
}

export interface InvestigationCause {
  layer: CauseLayer | string;
  title: string;
  explanation: string;
  status: EpistemicStatusWire;
  confidence: ConfidenceWire;
  evidenceIds: string[];
  action: BrokAction | null;
}

export interface InvestigationAnswer {
  question: string;
  answer: string;
  status: EpistemicStatusWire;
  basis: string;
}

export interface InvestigationReferences {
  artifacts: BrokRef[];
  evidence: BrokRef[];
  knowledge: BrokRef[];
  decisions: BrokRef[];
  revisions: BrokRef[];
  precedents: BrokRef[];
  relatedEvaluations: BrokRef[];
}

export interface Investigation {
  id: string;
  subject: BrokRef;
  verdict: BrokVerdict;
  timeline: InvestigationEvent[];
  causes: InvestigationCause[];
  story: InvestigationAnswer[];
  impact: BrokImpact;
  references: InvestigationReferences;
  memory: BrokMemory[];
  recommendations: BrokRecommendation[];
  followUps: BrokFollowUp[];
  context: BrokContext;
  at: string;
}

export const investigationApi = {
  ofEvaluation: (organizationId: string, evaluationId: string, params: { projectId?: string } = {}) =>
    apiClient
      .get<Investigation>(
        `/api/v1/organizations/${organizationId}/investigations/evaluation/${evaluationId}`,
        { params },
      )
      .then((r) => r.data),
};
