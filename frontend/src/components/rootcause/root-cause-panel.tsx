"use client";

import { Search } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import {
  ConfidenceBadge,
  KnowledgeKeyBadge,
  SeverityBadge,
} from "@/components/common/severity";
import { useJobRootCause } from "@/lib/hooks/use-root-cause";
import { severityRank } from "@/lib/format";
import type { RootCauseFindingResponse } from "@/lib/api/root-cause";

/** Root-cause analysis for a single evaluation job. */
export function RootCausePanel({
  organizationId,
  projectId,
  jobId,
}: {
  organizationId: string;
  projectId: string;
  jobId: string;
}) {
  const { data, isLoading, isError } = useJobRootCause(organizationId, projectId, jobId);

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-32 w-full" />
        ))}
      </div>
    );
  }

  if (isError || !data) {
    return (
      <EmptyState icon={Search} title="Couldn't load analysis" description="Please try again." />
    );
  }

  const findings = [...data.findings].sort(
    (a, b) => severityRank(b.severity) - severityRank(a.severity),
  );

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm text-muted-foreground">
          {data.findingCount} {data.findingCount === 1 ? "finding" : "findings"}
        </span>
      </div>

      {data.notes.length > 0 && (
        <ul className="space-y-1 rounded-lg border border-border bg-muted/30 px-4 py-3 text-sm text-muted-foreground">
          {data.notes.map((note, i) => (
            <li key={i}>{note}</li>
          ))}
        </ul>
      )}

      {findings.length === 0 ? (
        <EmptyState
          icon={Search}
          title="No root causes identified"
          description="The analyzer found no significant failure patterns for this evaluation."
        />
      ) : (
        <div className="space-y-4">
          {findings.map((finding, i) => (
            <FindingCard key={`${finding.knowledgeKey ?? finding.rootCause}-${i}`} finding={finding} />
          ))}
        </div>
      )}
    </div>
  );
}

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="text-sm leading-relaxed text-foreground">{children}</div>
    </div>
  );
}

function FindingCard({ finding }: { finding: RootCauseFindingResponse }) {
  return (
    <Card>
      <CardContent className="space-y-4 p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <SeverityBadge severity={finding.severity} />
              <ConfidenceBadge confidence={finding.confidence} />
            </div>
            <h3 className="text-base font-semibold leading-tight text-foreground">
              {finding.rootCause}
            </h3>
          </div>
          {finding.knowledgeKey && <KnowledgeKeyBadge nodeKey={finding.knowledgeKey} />}
        </div>

        <div className="space-y-3">
          <Section label="Recommendation">{finding.recommendation}</Section>
          <Section label="Expected improvement">
            <span className="text-success">{finding.expectedImprovement}</span>
          </Section>
        </div>

        {finding.evidence.length > 0 && (
          <div className="space-y-1.5 border-t border-border pt-3">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Evidence
            </p>
            <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
              {finding.evidence.map((item, i) => (
                <li key={i}>{item}</li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
