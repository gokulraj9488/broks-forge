"use client";

import { AlertTriangle, FileSearch } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Badge } from "@/components/ui/badge";
import { usePromptRenderDebug } from "@/lib/hooks/use-evaluation-jobs";

/**
 * Surfaces exactly how a run's prompt template resolved against its dataset row: which
 * variables the template references, which of those had a value, the exact rendered text sent
 * to the model, and — the actionable part — which variables had no matching value at all. A
 * missing variable renders as silent empty text (see backend EvaluationJobExecutor), which reads
 * to a user as "the model ignored my data" when the data never reached the prompt in the first place.
 */
export function PromptRenderDebug({
  organizationId,
  projectId,
  jobId,
  runId,
  enabled = true,
}: {
  organizationId: string;
  projectId: string;
  jobId: string;
  runId: string;
  enabled?: boolean;
}) {
  const { data, isLoading, isError } = usePromptRenderDebug(
    organizationId,
    projectId,
    jobId,
    runId,
    enabled,
  );

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <EmptyState icon={FileSearch} title="Couldn't load render debug" description="Please try again." />
    );
  }

  const hasTemplate = data.variablesDetected.length > 0;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
        <span>
          Dataset row <span className="font-mono text-foreground">#{data.datasetItemSequence}</span>
        </span>
        {data.datasetItemId && (
          <span className="font-mono">
            item <span className="text-foreground">{data.datasetItemId}</span>
          </span>
        )}
      </div>

      {data.missingVariables.length > 0 && (
        <div className="flex items-start gap-2 rounded-md border border-warning/40 bg-warning/10 px-3 py-2 text-xs text-warning">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          <div>
            <span className="font-medium">
              {data.missingVariables.length === 1 ? "This variable" : "These variables"} had no matching
              value and rendered as empty text:
            </span>{" "}
            {data.missingVariables.map((name) => (
              <Badge key={name} variant="warning" className="ml-1 font-mono text-[11px]">
                {`{{${name}}}`}
              </Badge>
            ))}
            <p className="mt-1 text-warning/80">
              Check the dataset&apos;s column mapping or the prompt&apos;s variable names — this is the
              usual cause of a model reporting missing data for some rows but not others.
            </p>
          </div>
        </div>
      )}

      {hasTemplate && (
        <div>
          <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Template variables
          </p>
          <div className="flex flex-wrap gap-1.5">
            {data.variablesDetected.map((name) => {
              const wasResolved = Object.prototype.hasOwnProperty.call(data.variablesResolved, name);
              return (
                <Badge
                  key={name}
                  variant={wasResolved ? "success" : "warning"}
                  className="font-mono text-[11px]"
                  title={wasResolved ? data.variablesResolved[name] : "No matching value"}
                >
                  {`{{${name}}}`}
                </Badge>
              );
            })}
          </div>
        </div>
      )}

      <div>
        <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
          Rendered prompt
          <span className="ml-2 font-normal normal-case text-muted-foreground/70">
            Exact text sent to the model for this run
          </span>
        </p>
        <pre className="max-h-[28rem] overflow-auto whitespace-pre-wrap break-words rounded-md border border-border bg-background p-3 text-xs">
          {data.renderedPrompt ?? "—"}
        </pre>
      </div>
    </div>
  );
}
