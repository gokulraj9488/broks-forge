"use client";

import { AlertTriangle, CheckCircle2, Circle, MinusCircle, Sparkles } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { AgentReadiness, OnboardingStepKey } from "@/lib/agent-readiness";

/** The exact, user-facing message shown wherever an unconfigured agent surfaces. */
export const CREDENTIALS_REQUIRED_MESSAGE =
  "Agent cannot be used until credentials are configured.";

/**
 * Persistent warning shown on Overview, Health, Advisor and the evaluation flow
 * whenever an agent that needs authentication has no credential yet.
 */
export function CredentialSetupAlert({
  onConfigure,
  className,
}: {
  onConfigure?: () => void;
  className?: string;
}) {
  return (
    <Alert variant="warning" className={cn("items-center", className)}>
      <AlertTriangle />
      <div className="flex-1">
        <AlertTitle>Credentials required</AlertTitle>
        <AlertDescription>{CREDENTIALS_REQUIRED_MESSAGE}</AlertDescription>
      </div>
      {onConfigure && (
        <Button size="sm" onClick={onConfigure} className="shrink-0">
          Configure
        </Button>
      )}
    </Alert>
  );
}

/** Compact header chip summarising an agent's readiness. */
export function AgentReadinessBadge({ readiness }: { readiness: AgentReadiness }) {
  if (readiness.isReady) return <Badge variant="success">Ready</Badge>;
  if (readiness.needsCredentialSetup) return <Badge variant="warning">Setup required</Badge>;
  return (
    <Badge variant="muted">
      Setup {readiness.completedCount}/{readiness.totalCount}
    </Badge>
  );
}

type StepAction = "credential" | "verify" | "health";

const NEXT_ACTION: Record<OnboardingStepKey, { label: string; action: StepAction } | null> = {
  registered: null,
  credential: { label: "Configure credential", action: "credential" },
  connection: { label: "Verify connection", action: "verify" },
  health: { label: "Run health check", action: "health" },
};

function StepIcon({ done, notApplicable }: { done: boolean; notApplicable: boolean }) {
  if (notApplicable) return <MinusCircle className="h-5 w-5 text-muted-foreground/60" />;
  if (done) return <CheckCircle2 className="h-5 w-5 text-success" />;
  return <Circle className="h-5 w-5 text-muted-foreground" />;
}

/**
 * The four-step onboarding checklist. Guides the user to the single next action
 * until the agent is Ready, then collapses into a success confirmation.
 */
export function AgentReadinessChecklist({
  readiness,
  onConfigureCredential,
  onVerifyConnection,
  onRunHealth,
  runningHealth,
  verifyingConnection,
  className,
}: {
  readiness: AgentReadiness;
  onConfigureCredential?: () => void;
  onVerifyConnection?: () => void;
  onRunHealth?: () => void;
  runningHealth?: boolean;
  verifyingConnection?: boolean;
  className?: string;
}) {
  const nextStep = readiness.steps.find((s) => !s.notApplicable && !s.done);
  const nextAction = nextStep ? NEXT_ACTION[nextStep.key] : null;

  const runAction = (action: StepAction) => {
    if (action === "credential") onConfigureCredential?.();
    else if (action === "verify") onVerifyConnection?.();
    else onRunHealth?.();
  };

  const actionLoading = (action: StepAction) =>
    action === "health" ? runningHealth : action === "verify" ? verifyingConnection : undefined;

  return (
    <Card
      className={cn(
        "border-l-4",
        readiness.isReady ? "border-l-success" : "border-l-primary",
        className,
      )}
    >
      <CardContent className="space-y-4 p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-2">
            {readiness.isReady ? (
              <Sparkles className="h-5 w-5 text-success" />
            ) : (
              <Sparkles className="h-5 w-5 text-primary" />
            )}
            <div>
              <h3 className="text-sm font-semibold">
                {readiness.isReady ? "Agent is ready" : "Finish setting up this agent"}
              </h3>
              <p className="text-xs text-muted-foreground">
                {readiness.isReady
                  ? "All setup steps are complete."
                  : `${readiness.completedCount} of ${readiness.totalCount} steps complete`}
              </p>
            </div>
          </div>
          <AgentReadinessBadge readiness={readiness} />
        </div>

        <ol className="space-y-2">
          {readiness.steps.map((step) => {
            const isNext = step.key === nextStep?.key;
            return (
              <li key={step.key} className="flex items-center gap-3">
                <StepIcon done={step.done} notApplicable={step.notApplicable} />
                <span
                  className={cn(
                    "flex-1 text-sm",
                    step.done && "text-foreground",
                    !step.done && !step.notApplicable && "text-muted-foreground",
                    step.notApplicable && "text-muted-foreground/60",
                  )}
                >
                  {step.label}
                  {step.notApplicable && (
                    <span className="ml-1.5 text-xs text-muted-foreground/60">· not required</span>
                  )}
                </span>
                {isNext && nextAction && (
                  <Button
                    size="sm"
                    onClick={() => runAction(nextAction.action)}
                    loading={actionLoading(nextAction.action)}
                  >
                    {nextAction.label}
                  </Button>
                )}
              </li>
            );
          })}
        </ol>
      </CardContent>
    </Card>
  );
}
