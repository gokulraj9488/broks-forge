/**
 * Maps an engineering artifact to its existing management page (its "engineering workspace"). Shared by the
 * evolution, intelligence and registry views so related artifacts always navigate to the current product pages
 * — no new routes are invented. An optional {@code tab} lands the visitor directly on a workspace tab (e.g.
 * "intelligence") so arriving from Knowledge or the Graph feels like opening a workspace, not a redirect.
 */
export function artifactHref(
  org: string,
  type: string,
  entityId: string | null,
  projectId: string | null,
  opts: { tab?: string } = {},
): string | null {
  const tab = opts.tab ? `?tab=${opts.tab}` : "";
  const scoped = (path: string) => `/organizations/${org}/projects/${projectId}${path}${tab}`;
  switch (type) {
    case "project":
      return entityId ? `/organizations/${org}/projects/${entityId}` : null;
    case "agent":
      return projectId && entityId ? scoped(`/agents/${entityId}`) : null;
    case "prompt":
      return projectId && entityId ? scoped(`/prompts/${entityId}`) : null;
    case "dataset":
      return projectId && entityId ? scoped(`/datasets/${entityId}`) : null;
    case "evaluation":
      return projectId && entityId ? scoped(`/evaluations/${entityId}`) : null;
    case "provider":
      return "/providers";
    default:
      return null;
  }
}

/**
 * The dedicated page for one engineering-knowledge object (Observation / Claim / Decision / Evidence /
 * Knowledge). Composite ids are URL-encoded so their colons survive routing.
 */
export function knowledgeHref(org: string, id: string): string {
  return `/organizations/${org}/intelligence/${encodeURIComponent(id)}`;
}

/**
 * The Root Cause Explorer for one evaluation. It hangs off the evaluation's own workspace rather than
 * living at a top-level route, because an investigation is always <em>of</em> something — the URL should
 * say what, and the back path should be obvious.
 */
export function investigationHref(
  org: string,
  evaluationId: string | null,
  projectId: string | null,
): string | null {
  return projectId && evaluationId
    ? `/organizations/${org}/projects/${projectId}/evaluations/${evaluationId}/investigate`
    : null;
}
