"use client";

import { useState } from "react";
import {
  Brain,
  Clock,
  Code2,
  Headset,
  Languages,
  LayoutGrid,
  ListChecks,
  Search,
  ShieldCheck,
  ShieldQuestion,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { ProvisionTemplateDialog } from "@/components/benchmarks/provision-template-dialog";
import { useGalleryTemplates } from "@/lib/hooks/use-benchmark-gallery";
import type {
  GalleryTemplateDifficulty,
  GalleryTemplateKey,
  GalleryTemplateResponse,
} from "@/lib/api/benchmark-gallery";

const TEMPLATE_ICONS: Record<GalleryTemplateKey, typeof LayoutGrid> = {
  CUSTOMER_SUPPORT: Headset,
  RAG: Search,
  CODING: Code2,
  REASONING: Brain,
  HALLUCINATION: ShieldQuestion,
  SAFETY: ShieldCheck,
  SUMMARIZATION: ListChecks,
  TRANSLATION: Languages,
};

const DIFFICULTY_VARIANT: Record<GalleryTemplateDifficulty, "success" | "warning" | "destructive"> = {
  EASY: "success",
  MEDIUM: "warning",
  HARD: "destructive",
};

export function BenchmarkGalleryPanel({
  organizationId,
  projectId,
  canManage,
}: {
  organizationId: string;
  projectId: string;
  canManage: boolean;
}) {
  const { data: templates, isLoading, isError } = useGalleryTemplates(organizationId, projectId);
  const [selected, setSelected] = useState<GalleryTemplateResponse | null>(null);

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-base font-semibold">Benchmark Gallery</h2>
        <p className="text-sm text-muted-foreground">
          Curated templates — each provisions a starter dataset, prompt, and scoring profile, then runs in one click.
        </p>
      </div>

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-48 w-full" />
          ))}
        </div>
      ) : isError || !templates ? (
        <EmptyState icon={LayoutGrid} title="Couldn't load gallery templates" description="Please try again." />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {templates.map((template) => {
            const Icon = TEMPLATE_ICONS[template.key] ?? LayoutGrid;
            return (
              <Card key={template.key} className="flex h-full flex-col hover:border-primary/30">
                <CardContent className="flex flex-1 flex-col p-6">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                      <Icon className="h-5 w-5 text-primary" />
                    </div>
                    <div className="flex flex-col items-end gap-1.5">
                      <Badge variant="muted">{template.category}</Badge>
                      <Badge variant={DIFFICULTY_VARIANT[template.difficulty]} className="text-[10px]">
                        {template.difficulty}
                      </Badge>
                    </div>
                  </div>
                  <h3 className="mt-4 font-medium leading-tight">{template.name}</h3>
                  <p className="mt-1.5 flex-1 text-xs text-muted-foreground">{template.description}</p>
                  <div className="mt-4 flex flex-wrap gap-1.5">
                    {template.metrics.map((metric, i) => (
                      <Badge key={i} variant="outline" className="text-[10px]">
                        {metric.label ?? metric.type}
                      </Badge>
                    ))}
                  </div>
                  <div className="mt-5 flex items-center justify-between border-t border-border/60 pt-4 text-xs text-muted-foreground">
                    <div className="flex items-center gap-3">
                      <span>{template.datasetItemCount} starter items</span>
                      <span className="flex items-center gap-1">
                        <Clock className="h-3.5 w-3.5" />
                        ~{template.estimatedRuntimeMinutes} min
                      </span>
                    </div>
                    {canManage && (
                      <Button size="sm" onClick={() => setSelected(template)}>
                        Import
                      </Button>
                    )}
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {selected && (
        <ProvisionTemplateDialog
          organizationId={organizationId}
          projectId={projectId}
          template={selected}
          open={!!selected}
          onOpenChange={(open) => !open && setSelected(null)}
        />
      )}
    </div>
  );
}
