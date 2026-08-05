"use client";

import { use } from "react";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { PageHeader } from "@/components/layout/page-header";
import { InvestigationWorkspace } from "@/components/investigation/investigation-workspace";

/**
 * The Root Cause Explorer for one evaluation.
 *
 * A route of its own rather than a tab, because an investigation is a place an engineer goes and stays:
 * it deserves the full width, a back path to the evaluation it came from, and a URL that can be pasted
 * into an incident channel.
 */
export default function InvestigatePage({
  params,
}: {
  params: Promise<{ orgId: string; projectId: string; jobId: string }>;
}) {
  const { orgId, projectId, jobId } = use(params);

  return (
    <div className="space-y-2">
      <Link
        href={`/organizations/${orgId}/projects/${projectId}/evaluations/${jobId}`}
        className="inline-flex items-center gap-1.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to the evaluation
      </Link>
      <PageHeader
        title="Investigation"
        description="Everything the engineering record holds about this failure — the chronology that led to it, the cause at four depths, and what to do next."
      />
      <InvestigationWorkspace
        organizationId={orgId}
        projectId={projectId}
        evaluationId={jobId}
      />
    </div>
  );
}
