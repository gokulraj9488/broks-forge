import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  ConfidenceBadge,
  KnowledgeKeyBadge,
  SeverityBadge,
} from "@/components/common/severity";
import { humanize } from "@/lib/format";
import type { RecommendationResponse } from "@/lib/api/advisor";

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="text-sm leading-relaxed text-foreground">{children}</div>
    </div>
  );
}

/** Renders a single advisory recommendation. */
export function RecommendationCard({
  recommendation,
}: {
  recommendation: RecommendationResponse;
}) {
  const rec = recommendation;
  return (
    <Card>
      <CardContent className="space-y-4 p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline">{humanize(rec.category)}</Badge>
              <SeverityBadge severity={rec.severity} />
              <ConfidenceBadge confidence={rec.confidence} />
            </div>
            <h3 className="text-base font-semibold leading-tight text-foreground">{rec.title}</h3>
          </div>
          {rec.knowledgeKey && <KnowledgeKeyBadge nodeKey={rec.knowledgeKey} />}
        </div>

        <div className="space-y-3">
          <Section label="Why">{rec.why}</Section>
          {rec.whatChanged && <Section label="What changed">{rec.whatChanged}</Section>}
          <Section label="How to fix">{rec.howToFix}</Section>
          {rec.expectedImprovement && (
            <Section label="Expected improvement">
              <span className="text-success">{rec.expectedImprovement}</span>
            </Section>
          )}
        </div>

        {rec.evidence.length > 0 && (
          <div className="space-y-1.5 border-t border-border pt-3">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Evidence
            </p>
            <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
              {rec.evidence.map((item, i) => (
                <li key={i}>{item}</li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
