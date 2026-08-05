import type { BrokAction } from "@/lib/api/brok";
import { artifactHref, investigationHref, knowledgeHref } from "@/lib/artifact-links";

/**
 * Resolves a Brok action to the route of an <b>existing</b> surface.
 *
 * <p>Brok never ends at an answer: every recommendation continues into the workflow the answer was
 * derived from. This module is the single place that knows how an action kind maps onto the product's real
 * routes, so the API stays free of URLs and no new destinations can be invented — an action that cannot be
 * resolved here simply renders without a link rather than pointing somewhere that does not exist.
 */
export function brokActionHref(organizationId: string, action: BrokAction | null): string | null {
  if (!action) {
    return null;
  }
  const type = action.targetType ?? "";
  const entityId = action.entityId;
  const projectId = action.projectId;

  switch (action.kind) {
    case "openGraph":
      // The Forge Graph lives on /knowledge; a target focuses it on the node the answer was about.
      return action.targetId ? `/knowledge?focus=${encodeURIComponent(action.targetId)}` : "/knowledge";
    case "openExecutionGraph":
      return artifactHref(organizationId, "evaluation", entityId, projectId, { tab: "execution" });
    case "openFailureGraph": {
      // The Failure Graph is the Execution Graph already narrowed to the broken links — same surface,
      // the state the answer was about, so the engineer never has to re-apply Brok's own filter.
      const href = artifactHref(organizationId, "evaluation", entityId, projectId, { tab: "execution" });
      return href ? `${href}&view=failures` : null;
    }
    case "openEvaluation":
      return artifactHref(organizationId, "evaluation", entityId, projectId);
    case "openIntelligence":
      return artifactHref(organizationId, type, entityId, projectId, { tab: "intelligence" });
    case "openEvolution":
      return artifactHref(organizationId, type, entityId, projectId, { tab: "evolution" });
    case "openRevisions":
    case "compareRevisions":
      // AI Git — an artifact's real revision timeline and comparison — is the Evolution workspace.
      return artifactHref(organizationId, type, entityId, projectId, { tab: "evolution" });
    case "openKnowledge":
      return action.targetId ? knowledgeHref(organizationId, action.targetId) : "/registry";
    case "openRegistry":
      return "/registry";
    case "openAnalytics":
      return "/analytics";
    case "openInsights":
      return "/insights";
    case "startInvestigation": {
      // "Investigate this" on an evaluation opens the Root Cause Explorer (P13) — the whole investigation
      // already assembled — rather than a fresh conversation about it. For anything else there is no
      // assembled investigation to open, so it stays what it was: Brok, entered deliberately about one
      // object with one question. Either way the engineer lands on work, not on a blank prompt.
      if (action.targetId?.startsWith("evaluation:")) {
        const evaluationId = action.targetId.slice("evaluation:".length);
        const href = investigationHref(organizationId, evaluationId, projectId);
        if (href) {
          return href;
        }
      }
      const params = new URLSearchParams();
      if (action.targetId) params.set("focus", action.targetId);
      if (action.question) params.set("q", action.question);
      return `/brok?${params.toString()}`;
    }
    default:
      return null;
  }
}

/**
 * Where a referenced record opens. Knowledge objects get their own page; artifacts open their workspace on
 * the tab that shows the reasoning Brok just used.
 */
export function brokRefHref(
  organizationId: string,
  ref: { id: string; type: string; entityId: string | null; projectId: string | null },
): string | null {
  const reasoning = ["observation", "claim", "decision", "evidence", "knowledge"];
  if (reasoning.includes(ref.type)) {
    return knowledgeHref(organizationId, ref.id);
  }
  if (ref.type === "revision") {
    // A revision belongs to its own artifact's Evolution workspace — the kind is in the revision id.
    const artifactType = ref.id.startsWith("agent-version:")
      ? "agent"
      : ref.id.startsWith("dataset-version:")
        ? "dataset"
        : "prompt";
    return artifactHref(organizationId, artifactType, ref.entityId, ref.projectId, { tab: "evolution" });
  }
  if (ref.type === "run") {
    // A sampled failed run belongs to its evaluation's Runs tab.
    return artifactHref(organizationId, "evaluation", ref.entityId, ref.projectId, { tab: "runs" });
  }
  return artifactHref(organizationId, ref.type, ref.entityId, ref.projectId);
}
