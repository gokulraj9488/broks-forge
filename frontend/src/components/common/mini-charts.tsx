"use client";

import { cn } from "@/lib/utils";

// ---------------------------------------------------------------------------
// Dependency-free inline-SVG charts. Intentionally minimal — enough for the
// trend strips and comparison bars on the analytics / dashboard surfaces.
// ---------------------------------------------------------------------------

export interface SparkPoint {
  label: string;
  value: number;
}

/** Smooth-ish area + line sparkline. Scales to its container width. */
export function Sparkline({
  data,
  height = 56,
  className,
  tone = "primary",
}: {
  data: SparkPoint[];
  height?: number;
  className?: string;
  tone?: "primary" | "success";
}) {
  const width = 100; // viewBox units; SVG stretches via preserveAspectRatio
  if (data.length === 0) {
    return (
      <div
        className={cn("flex items-center justify-center text-xs text-muted-foreground", className)}
        style={{ height }}
      >
        No data
      </div>
    );
  }

  const values = data.map((d) => d.value);
  const max = Math.max(...values, 1);
  const min = Math.min(...values, 0);
  const range = max - min || 1;
  const stepX = data.length > 1 ? width / (data.length - 1) : width;

  const points = data.map((d, i) => {
    const x = data.length > 1 ? i * stepX : width / 2;
    const y = height - ((d.value - min) / range) * (height - 4) - 2;
    return [x, y] as const;
  });

  const line = points.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`).join(" ");
  const area = `${line} L${width},${height} L0,${height} Z`;
  const stroke = tone === "success" ? "hsl(var(--success))" : "hsl(var(--chart-1))";

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      className={cn("w-full", className)}
      style={{ height }}
      role="img"
      aria-label="Trend sparkline"
    >
      <path d={area} fill={stroke} opacity={0.12} />
      <path d={line} fill="none" stroke={stroke} strokeWidth={1.5} vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

export interface BarDatum {
  label: string;
  value: number;
  /** Optional formatted value shown at the end of the bar. */
  display?: string;
  highlight?: boolean;
}

/** Horizontal labelled bar chart for comparisons / leaderboards. */
export function HBarChart({
  data,
  className,
  max,
}: {
  data: BarDatum[];
  className?: string;
  max?: number;
}) {
  if (data.length === 0) {
    return <p className={cn("text-sm text-muted-foreground", className)}>No data to chart.</p>;
  }
  const maxValue = max ?? Math.max(...data.map((d) => d.value), 1);

  return (
    <div className={cn("space-y-3", className)}>
      {data.map((d, i) => {
        const pct = Math.max(0, (d.value / maxValue) * 100);
        return (
          <div key={`${d.label}-${i}`} className="space-y-1">
            <div className="flex items-center justify-between gap-2 text-xs">
              <span className="truncate text-foreground">{d.label}</span>
              <span className="shrink-0 font-mono text-muted-foreground">
                {d.display ?? d.value}
              </span>
            </div>
            <div className="h-2.5 w-full overflow-hidden rounded-full bg-muted">
              <div
                className={cn(
                  "h-full rounded-full transition-[width] duration-300 ease-out",
                  d.highlight ? "bg-chart-1" : "bg-chart-1/50",
                )}
                style={{ width: `${pct}%` }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
