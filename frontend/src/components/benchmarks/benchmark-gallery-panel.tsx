"use client";

import { useState } from "react";
import { LayoutGrid } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { ProvisionTemplateDialog } from "@/components/benchmarks/provision-template-dialog";
import { useGalleryTemplates } from "@/lib/hooks/use-benchmark-gallery";
import type { GalleryTemplateResponse } from "@/lib/api/benchmark-gallery";

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
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-40 w-full" />
          ))}
        </div>
      ) : isError || !templates ? (
        <EmptyState icon={LayoutGrid} title="Couldn't load gallery templates" description="Please try again." />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {templates.map((template) => (
            <Card key={template.key} className="flex h-full flex-col">
              <CardContent className="flex flex-1 flex-col p-5">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                    <LayoutGrid className="h-5 w-5 text-primary" />
                  </div>
                  <Badge variant="muted">{template.category}</Badge>
                </div>
                <h3 className="mt-3 font-medium leading-tight">{template.name}</h3>
                <p className="mt-1 flex-1 text-xs text-muted-foreground">{template.description}</p>
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {template.metrics.map((metric, i) => (
                    <Badge key={i} variant="outline" className="text-[10px]">
                      {metric.label ?? metric.type}
                    </Badge>
                  ))}
                </div>
                <div className="mt-4 flex items-center justify-between text-xs text-muted-foreground">
                  <span>{template.datasetItemCount} starter items</span>
                  {canManage && (
                    <Button size="sm" variant="outline" onClick={() => setSelected(template)}>
                      Use template
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
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
