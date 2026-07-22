import type { LucideIcon } from "lucide-react";
import { ArrowDown, ArrowUp } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface StatCardProps {
  label: string;
  value: React.ReactNode;
  hint?: React.ReactNode;
  icon?: LucideIcon;
  className?: string;
  /**
   * A directional change since the last comparable period. `good` colors it success/destructive
   * from the metric's own perspective — e.g. a latency drop is "good" (success) even though the
   * number itself went down, whereas a cost drop being "good" also happens to point down; the
   * caller decides which direction is favorable rather than this component assuming higher-is-better.
   */
  delta?: { value: string; good: boolean; direction: "up" | "down" };
  /** Optional inline visual (sparkline, mini histogram) rendered under the value. */
  chart?: React.ReactNode;
}

/** Compact metric tile used across dashboards, analytics and eval summaries. */
export function StatCard({ label, value, hint, icon: Icon, className, delta, chart }: StatCardProps) {
  return (
    <Card className={className}>
      <CardContent className="p-5">
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm text-muted-foreground">{label}</p>
          {Icon && <Icon className="h-4 w-4 text-muted-foreground" />}
        </div>
        <p className="mt-2 text-2xl font-semibold tracking-tight">{value}</p>
        {(delta || hint != null) && (
          <div className="mt-1 flex items-center gap-1.5">
            {delta && (
              <span
                className={cn(
                  "flex items-center gap-0.5 text-xs font-medium",
                  delta.good ? "text-success" : "text-destructive",
                )}
              >
                {delta.direction === "up" ? (
                  <ArrowUp className="h-3 w-3" />
                ) : (
                  <ArrowDown className="h-3 w-3" />
                )}
                {delta.value}
              </span>
            )}
            {hint != null && <p className="text-xs text-muted-foreground">{hint}</p>}
          </div>
        )}
        {chart && <div className="mt-3">{chart}</div>}
      </CardContent>
    </Card>
  );
}

/** Horizontal progress meter (0..1) — used for pass rates and coverage. */
export function MeterBar({
  value,
  className,
  tone = "primary",
}: {
  value: number; // 0..1
  className?: string;
  tone?: "primary" | "success" | "destructive";
}) {
  const pct = Math.max(0, Math.min(1, value)) * 100;
  const toneClass =
    tone === "success"
      ? "bg-success"
      : tone === "destructive"
        ? "bg-destructive"
        : "bg-primary";
  return (
    <div
      role="progressbar"
      aria-valuenow={Math.round(pct)}
      aria-valuemin={0}
      aria-valuemax={100}
      className={cn("h-2 w-full overflow-hidden rounded-full bg-muted", className)}
    >
      <div
        className={cn("h-full rounded-full transition-[width] duration-300 ease-out", toneClass)}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}
