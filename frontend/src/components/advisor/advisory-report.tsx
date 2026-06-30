import { Lightbulb } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { RecommendationCard } from "@/components/advisor/recommendation-card";
import { humanize, severityBadgeVariant, severityRank } from "@/lib/format";
import type { AdvisoryReportResponse } from "@/lib/api/advisor";

/** Renders a full advisory report: severity summary, notes and recommendations. */
export function AdvisoryReport({ report }: { report: AdvisoryReportResponse }) {
  const breakdown = [...report.severityBreakdown]
    .filter((b) => b.count > 0)
    .sort((a, b) => severityRank(b.severity) - severityRank(a.severity));

  const recommendations = [...report.recommendations].sort(
    (a, b) => severityRank(b.severity) - severityRank(a.severity),
  );

  if (report.recommendationCount === 0 && recommendations.length === 0) {
    return (
      <div className="space-y-4">
        {report.notes.length > 0 && <Notes notes={report.notes} />}
        <EmptyState
          icon={Lightbulb}
          title="No recommendations"
          description="This subject looks healthy. The advisor found nothing to improve right now."
        />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm text-muted-foreground">
          {report.recommendationCount}{" "}
          {report.recommendationCount === 1 ? "recommendation" : "recommendations"}
        </span>
        {breakdown.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            {breakdown.map((b) => (
              <Badge key={b.severity} variant={severityBadgeVariant(b.severity)}>
                {humanize(b.severity)} · {b.count}
              </Badge>
            ))}
          </div>
        )}
      </div>

      {report.notes.length > 0 && <Notes notes={report.notes} />}

      <div className="space-y-4">
        {recommendations.map((rec, i) => (
          <RecommendationCard key={`${rec.knowledgeKey ?? rec.title}-${i}`} recommendation={rec} />
        ))}
      </div>
    </div>
  );
}

function Notes({ notes }: { notes: string[] }) {
  return (
    <ul className="space-y-1 rounded-lg border border-border bg-muted/30 px-4 py-3 text-sm text-muted-foreground">
      {notes.map((note, i) => (
        <li key={i}>{note}</li>
      ))}
    </ul>
  );
}
