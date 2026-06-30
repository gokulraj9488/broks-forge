import { Badge } from "@/components/ui/badge";
import {
  confidenceBadgeVariant,
  humanize,
  severityBadgeVariant,
} from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * Shared severity / confidence presentation for the Phase 4 advisor,
 * root-cause, debugger and knowledge surfaces. Wraps the badge-variant
 * helpers in `lib/format` so every surface renders these consistently.
 */
export function SeverityBadge({
  severity,
  className,
}: {
  severity: string | null | undefined;
  className?: string;
}) {
  if (!severity) return null;
  return (
    <Badge variant={severityBadgeVariant(severity)} className={className}>
      {humanize(severity)}
    </Badge>
  );
}

export function ConfidenceBadge({
  confidence,
  className,
}: {
  confidence: string | null | undefined;
  className?: string;
}) {
  if (!confidence) return null;
  return (
    <Badge variant={confidenceBadgeVariant(confidence)} className={cn("gap-1", className)}>
      {humanize(confidence)} confidence
    </Badge>
  );
}

/** A subtle, monospaced knowledge-key reference badge. */
export function KnowledgeKeyBadge({
  nodeKey,
  className,
}: {
  nodeKey: string | null | undefined;
  className?: string;
}) {
  if (!nodeKey) return null;
  return (
    <span
      className={cn(
        "inline-flex items-center rounded border border-border bg-muted/60 px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground",
        className,
      )}
      title="Engineering knowledge reference"
    >
      {nodeKey}
    </span>
  );
}
