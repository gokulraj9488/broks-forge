"use client";

import { useEffect, useState } from "react";
import { ArrowRight, GitCompare, Minus, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { usePromptCompare, usePromptVersions } from "@/lib/hooks/use-prompts";

export function PromptComparePanel({
  organizationId,
  projectId,
  promptId,
}: {
  organizationId: string;
  projectId: string;
  promptId: string;
}) {
  const { data: versionsData, isLoading: versionsLoading } = usePromptVersions(
    organizationId,
    projectId,
    promptId,
    { size: 100 },
  );
  const versions = versionsData?.content ?? [];

  const [from, setFrom] = useState<string | undefined>();
  const [to, setTo] = useState<string | undefined>();

  useEffect(() => {
    if (from != null || versions.length < 2) return;
    const sorted = [...versions].sort((a, b) => a.versionNumber - b.versionNumber);
    setFrom(sorted[sorted.length - 2].id);
    setTo(sorted[sorted.length - 1].id);
  }, [versions, from]);

  const { data, isLoading, isError } = usePromptCompare(
    organizationId,
    projectId,
    promptId,
    from,
    to,
  );

  if (versionsLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  if (versions.length < 2) {
    return (
      <EmptyState
        icon={GitCompare}
        title="Not enough versions"
        description="Create at least two versions to compare their variables."
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">From</span>
          <Select value={from ?? ""} onValueChange={setFrom}>
            <SelectTrigger
              className="h-8 w-auto min-w-[8rem] text-xs"
              aria-label="From version"
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {versions.map((v) => (
                <SelectItem key={v.id} value={v.id}>
                  v{v.versionNumber}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <ArrowRight className="h-4 w-4 text-muted-foreground" />
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">To</span>
          <Select value={to ?? ""} onValueChange={setTo}>
            <SelectTrigger
              className="h-8 w-auto min-w-[8rem] text-xs"
              aria-label="To version"
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {versions.map((v) => (
                <SelectItem key={v.id} value={v.id}>
                  v{v.versionNumber}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {from === to ? (
        <EmptyState icon={GitCompare} title="Pick two different versions" />
      ) : isLoading ? (
        <Skeleton className="h-48 w-full" />
      ) : isError || !data ? (
        <EmptyState icon={GitCompare} title="Couldn't compare versions" description="Please try again." />
      ) : (
        <div className="space-y-4">
          <Card>
            <CardContent className="flex items-center gap-3 p-5">
              <Badge variant={data.identicalTemplate ? "muted" : "default"}>
                {data.identicalTemplate ? "Templates identical" : "Templates differ"}
              </Badge>
              <span className="text-sm text-muted-foreground">
                Comparing v{data.from.versionNumber} → v{data.to.versionNumber}
              </span>
            </CardContent>
          </Card>

          <div className="grid gap-4 sm:grid-cols-3">
            <VariableList
              title="Added"
              icon={Plus}
              tone="success"
              variables={data.addedVariables}
            />
            <VariableList
              title="Removed"
              icon={Minus}
              tone="destructive"
              variables={data.removedVariables}
            />
            <VariableList title="Common" variables={data.commonVariables} />
          </div>
        </div>
      )}
    </div>
  );
}

function VariableList({
  title,
  variables,
  icon: Icon,
  tone = "muted",
}: {
  title: string;
  variables: string[];
  icon?: typeof Plus;
  tone?: "success" | "destructive" | "muted";
}) {
  const toneClass =
    tone === "success"
      ? "text-success"
      : tone === "destructive"
        ? "text-destructive"
        : "text-muted-foreground";
  return (
    <Card>
      <CardContent className="space-y-3 p-5">
        <div className={`flex items-center gap-1.5 text-sm font-medium ${toneClass}`}>
          {Icon && <Icon className="h-4 w-4" />}
          {title} ({variables.length})
        </div>
        {variables.length === 0 ? (
          <p className="text-xs text-muted-foreground">None</p>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {variables.map((v) => (
              <code key={v} className="rounded bg-muted px-1.5 py-0.5 text-[11px]">
                {`{{${v}}}`}
              </code>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
