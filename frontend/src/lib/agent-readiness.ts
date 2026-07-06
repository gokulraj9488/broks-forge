import type {
  AgentCredentialResponse,
  AgentHealthSummaryResponse,
  AgentResponse,
} from "@/lib/api/agents";

/**
 * Agent onboarding / readiness model.
 *
 * Readiness is derived entirely from data (no persisted "onboarding complete"
 * flag): an agent is Ready once it is registered, has an active credential
 * (when its auth type needs one), that credential's connection has been
 * verified, and a health check has passed.
 */

export type OnboardingStepKey = "registered" | "credential" | "connection" | "health";

export interface OnboardingStep {
  key: OnboardingStepKey;
  label: string;
  done: boolean;
  /** Not applicable to this agent (e.g. credential steps when auth type is NONE). */
  notApplicable: boolean;
}

export interface AgentReadiness {
  /** The agent's auth type needs a stored secret (anything other than NONE). */
  requiresCredential: boolean;
  /** An active credential exists (always true when no credential is required). */
  credentialConfigured: boolean;
  /** The active credential's connection test succeeded. */
  connectionVerified: boolean;
  /** The most recent health status is HEALTHY. */
  healthPassed: boolean;
  /** The agent is unusable until a credential is configured. */
  needsCredentialSetup: boolean;
  steps: OnboardingStep[];
  completedCount: number;
  totalCount: number;
  isReady: boolean;
}

/**
 * Whether an agent still needs credentials before it can be used. Works on both
 * the full {@link AgentResponse} and the lighter summary — anything carrying
 * `authType` + `credentialConfigured`. Drives gating and "setup required" flags.
 */
export function agentNeedsCredentialSetup(
  agent: { authType: AgentResponse["authType"]; credentialConfigured: boolean },
): boolean {
  return agent.authType !== "NONE" && !agent.credentialConfigured;
}

export function computeAgentReadiness(
  agent: AgentResponse,
  credentials: AgentCredentialResponse[] | undefined,
  health: AgentHealthSummaryResponse | undefined,
): AgentReadiness {
  const requiresCredential = agent.authType !== "NONE";
  const activeCredential = credentials?.find((c) => c.active);

  // Prefer the freshly-loaded credential list; fall back to the server-computed
  // flag when the list isn't available to this viewer (it is admin-only).
  const credentialConfigured = !requiresCredential
    ? true
    : credentials
      ? !!activeCredential
      : agent.credentialConfigured;

  const connectionVerified = !requiresCredential ? true : activeCredential?.lastTestSuccess === true;
  const healthPassed = health?.currentStatus === "HEALTHY";

  const steps: OnboardingStep[] = [
    { key: "registered", label: "Agent registered", done: true, notApplicable: false },
    {
      key: "credential",
      label: "Credential configured",
      done: credentialConfigured,
      notApplicable: !requiresCredential,
    },
    {
      key: "connection",
      label: "Connection verified",
      done: connectionVerified,
      notApplicable: !requiresCredential,
    },
    { key: "health", label: "Health check passed", done: healthPassed, notApplicable: false },
  ];

  const applicable = steps.filter((s) => !s.notApplicable);

  return {
    requiresCredential,
    credentialConfigured,
    connectionVerified,
    healthPassed,
    needsCredentialSetup: requiresCredential && !credentialConfigured,
    steps,
    completedCount: applicable.filter((s) => s.done).length,
    totalCount: applicable.length,
    isReady: applicable.every((s) => s.done),
  };
}
