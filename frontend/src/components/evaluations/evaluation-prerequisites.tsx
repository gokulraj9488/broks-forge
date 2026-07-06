"use client";

import type { LucideIcon } from "lucide-react";
import { Bot, Check, Database, FileText } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { RegisterAgentDialog } from "@/components/agents/register-agent-dialog";
import { CreateDatasetDialog } from "@/components/datasets/create-dataset-dialog";
import { CreatePromptDialog } from "@/components/prompts/create-prompt-dialog";
import { cn } from "@/lib/utils";

interface Props {
  organizationId: string;
  projectId: string;
  hasAgents: boolean;
  hasDatasets: boolean;
  hasPrompts: boolean;
}

function PrereqRow({
  done,
  optional,
  icon: Icon,
  title,
  description,
  cta,
}: {
  done: boolean;
  optional?: boolean;
  icon: LucideIcon;
  title: string;
  description: string;
  cta: React.ReactNode;
}) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-border p-3">
      <span
        className={cn(
          "flex h-8 w-8 shrink-0 items-center justify-center rounded-md",
          done ? "bg-success/15 text-success" : "bg-muted text-muted-foreground",
        )}
      >
        {done ? <Check className="h-4 w-4" /> : <Icon className="h-4 w-4" />}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-sm font-medium">{title}</p>
          {optional && <Badge variant="muted">Optional</Badge>}
          {done && <Badge variant="success">Ready</Badge>}
        </div>
        <p className="mt-0.5 text-xs text-muted-foreground">{description}</p>
      </div>
      {!done && <div className="shrink-0">{cta}</div>}
    </div>
  );
}

/**
 * Guided setup for the evaluation workflow — turns the old "you need a dataset"
 * dead-end into actionable steps, each with an inline create dialog.
 */
export function EvaluationPrerequisites({
  organizationId,
  projectId,
  hasAgents,
  hasDatasets,
  hasPrompts,
}: Props) {
  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">
        Set up what you need and run your first evaluation — nothing here is a dead end.
      </p>
      <PrereqRow
        done={hasAgents}
        icon={Bot}
        title="No active agents"
        description="Register the agent you want to evaluate (Spring AI, LangGraph, or a provider-backed agent)."
        cta={<RegisterAgentDialog organizationId={organizationId} projectId={projectId} />}
      />
      <PrereqRow
        done={hasDatasets}
        icon={Database}
        title="No datasets found"
        description="Import the inputs (CSV/JSON) to evaluate the agent against."
        cta={<CreateDatasetDialog organizationId={organizationId} projectId={projectId} />}
      />
      <PrereqRow
        done={hasPrompts}
        optional
        icon={FileText}
        title="No prompts found"
        description="Optionally add a prompt template to render each dataset item before invoking the agent."
        cta={<CreatePromptDialog organizationId={organizationId} projectId={projectId} />}
      />
    </div>
  );
}
