"use client";

import { cn } from "@/lib/utils";
import { Tooltip } from "@/components/ui/tooltip";

// ---------------------------------------------------------------------------
// Dependency-free inline-SVG charts. Intentionally minimal — enough for the
// trend strips and comparison bars on the analytics / dashboard surfaces.
// ---------------------------------------------------------------------------

export interface SparkPoint {
  label: string;
  value: number;
}

const TONE_COLOR: Record<string, string> = {
  primary: "hsl(var(--chart-1))",
  success: "hsl(var(--success))",
  warning: "hsl(var(--warning))",
  destructive: "hsl(var(--destructive))",
};

/** Shared point-plotting math so every line-based chart scales identically. */
function plotPoints(data: SparkPoint[], width: number, height: number, pad = 2) {
  const values = data.map((d) => d.value);
  const max = Math.max(...values, 1);
  const min = Math.min(...values, 0);
  const range = max - min || 1;
  const stepX = data.length > 1 ? width / (data.length - 1) : width;
  return data.map((d, i) => {
    const x = data.length > 1 ? i * stepX : width / 2;
    const y = height - ((d.value - min) / range) * (height - pad * 2) - pad;
    return [x, y] as const;
  });
}

function toPath(points: readonly (readonly [number, number])[]): string {
  return points.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`).join(" ");
}

function EmptyChart({ className, height }: { className?: string; height: number }) {
  return (
    <div
      className={cn("flex items-center justify-center text-xs text-muted-foreground", className)}
      style={{ height }}
    >
      No data
    </div>
  );
}

/** Area + line sparkline with a gradient fill — reserved for volume-style metrics (tokens). */
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
  if (data.length === 0) return <EmptyChart className={className} height={height} />;

  const points = plotPoints(data, width, height);
  const line = toPath(points);
  const area = `${line} L${width},${height} L0,${height} Z`;
  const stroke = TONE_COLOR[tone] ?? TONE_COLOR.primary;
  const gradientId = `spark-fill-${tone}`;

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      className={cn("w-full", className)}
      style={{ height }}
      role="img"
      aria-label="Trend sparkline"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={stroke} stopOpacity={0.28} />
          <stop offset="100%" stopColor={stroke} stopOpacity={0} />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradientId})`} />
      <path d={line} fill="none" stroke={stroke} strokeWidth={1.5} vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

/**
 * Discrete daily bars — for count/volume-over-time metrics (runs) where each day is a distinct
 * event, not a continuously varying quantity. Deliberately not a line: a line implies
 * interpolation between days that doesn't mean anything for a daily run count.
 */
export function BarTimeline({
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
  if (data.length === 0) return <EmptyChart className={className} height={height} />;

  const max = Math.max(...data.map((d) => d.value), 1);
  const fill = TONE_COLOR[tone] ?? TONE_COLOR.primary;

  return (
    <div className={cn("flex h-full items-end gap-[3px]", className)} style={{ height }}>
      {data.map((d, i) => {
        const pct = Math.max((d.value / max) * 100, d.value > 0 ? 6 : 2);
        const barHeight = Math.round((pct / 100) * height);
        return (
          <div
            key={`${d.label}-${i}`}
            className="flex flex-1 items-end [&>span]:flex [&>span]:w-full"
            style={{ height }}
          >
            <Tooltip content={`${d.label}: ${d.value}`}>
              <div
                className="w-full rounded-sm transition-[height] duration-300 ease-out"
                style={{ height: barHeight, backgroundColor: fill, opacity: 0.85 }}
              />
            </Tooltip>
          </div>
        );
      })}
    </div>
  );
}

/**
 * Raw line + a smoothed moving-average overlay — for noisy per-event metrics (latency) where the
 * trend matters more than any single spike. The muted raw line keeps spikes visible without
 * letting them read as "the trend."
 */
export function TrendLineChart({
  data,
  height = 56,
  className,
  tone = "primary",
  windowSize = 3,
}: {
  data: SparkPoint[];
  height?: number;
  className?: string;
  tone?: "primary" | "success";
  windowSize?: number;
}) {
  const width = 100;
  if (data.length === 0) return <EmptyChart className={className} height={height} />;

  const movingAverage: SparkPoint[] = data.map((d, i) => {
    const start = Math.max(0, i - windowSize + 1);
    const slice = data.slice(start, i + 1);
    const avg = slice.reduce((sum, p) => sum + p.value, 0) / slice.length;
    return { label: d.label, value: avg };
  });

  const rawPoints = plotPoints(data, width, height);
  const avgPoints = plotPoints(movingAverage, width, height);
  const stroke = TONE_COLOR[tone] ?? TONE_COLOR.primary;

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      className={cn("w-full", className)}
      style={{ height }}
      role="img"
      aria-label="Trend with moving average"
    >
      <path
        d={toPath(rawPoints)}
        fill="none"
        stroke={stroke}
        strokeOpacity={0.3}
        strokeWidth={1}
        vectorEffect="non-scaling-stroke"
      />
      <path
        d={toPath(avgPoints)}
        fill="none"
        stroke={stroke}
        strokeWidth={1.75}
        vectorEffect="non-scaling-stroke"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/**
 * Running-total line — for cumulative metrics (cost) where "how fast is this adding up" matters
 * more than any single day's spend. Monotonically non-decreasing by construction, which is what
 * visually separates it from a raw per-day series.
 */
export function CumulativeLineChart({
  data,
  height = 56,
  className,
  tone = "success",
}: {
  data: SparkPoint[];
  height?: number;
  className?: string;
  tone?: "primary" | "success";
}) {
  const width = 100;
  if (data.length === 0) return <EmptyChart className={className} height={height} />;

  let running = 0;
  const cumulative: SparkPoint[] = data.map((d) => {
    running += d.value;
    return { label: d.label, value: running };
  });

  const points = plotPoints(cumulative, width, height);
  const line = toPath(points);
  const area = `${line} L${width},${height} L0,${height} Z`;
  const stroke = TONE_COLOR[tone] ?? TONE_COLOR.success;
  const gradientId = `cumulative-fill-${tone}`;

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      className={cn("w-full", className)}
      style={{ height }}
      role="img"
      aria-label="Cumulative total"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={stroke} stopOpacity={0.18} />
          <stop offset="100%" stopColor={stroke} stopOpacity={0} />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradientId})`} />
      <path
        d={line}
        fill="none"
        stroke={stroke}
        strokeWidth={1.5}
        vectorEffect="non-scaling-stroke"
        strokeLinecap="round"
      />
    </svg>
  );
}

/** Ring chart for a single proportion (0..1) — pass rates, coverage, distribution shares. */
export function DonutChart({
  value,
  size = 96,
  strokeWidth = 10,
  tone = "primary",
  label,
}: {
  value: number;
  size?: number;
  strokeWidth?: number;
  tone?: "primary" | "success" | "warning" | "destructive";
  label?: string;
}) {
  const pct = Math.max(0, Math.min(1, value));
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - pct);
  const stroke = TONE_COLOR[tone] ?? TONE_COLOR.primary;

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90" role="img" aria-label={label ?? "Proportion"}>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="hsl(var(--muted))"
          strokeWidth={strokeWidth}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={stroke}
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          className="transition-[stroke-dashoffset] duration-500 ease-out"
        />
      </svg>
      <div className="absolute flex flex-col items-center">
        <span className="text-lg font-semibold tabular-nums text-foreground">
          {Math.round(pct * 100)}%
        </span>
        {label && <span className="text-[10px] text-muted-foreground">{label}</span>}
      </div>
    </div>
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
