"use client";

import { Lightbulb } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { AdvisoryReport } from "@/components/advisor/advisory-report";
import { useProjectAdvisory } from "@/lib/hooks/use-advisor";

/** Project-scoped engineering advisory. */
export function AdvisorPanel({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const { data, isLoading, isError } = useProjectAdvisory(organizationId, projectId);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-40 w-full" />
        ))}
      </div>
    );
  }

  if (isError || !data) {
    return (
      <EmptyState
        icon={Lightbulb}
        title="Couldn't load advisory"
        description="Please try again."
      />
    );
  }

  return <AdvisoryReport report={data} />;
}
