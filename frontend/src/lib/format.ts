// ---------------------------------------------------------------------------
// Lightweight value formatters for evaluation / analytics surfaces.
// Kept separate from utils.ts (date helpers) to avoid churn there.
// ---------------------------------------------------------------------------

const DASH = "—";

/** Format a 0..1 ratio as a percentage, e.g. 0.873 -> "87.3%". */
export function formatPercent(value: number | null | undefined, fractionDigits = 1): string {
  if (value == null || Number.isNaN(value)) return DASH;
  return `${(value * 100).toFixed(fractionDigits)}%`;
}

/** Format a number that is already a percentage (0..100). */
export function formatPercentValue(value: number | null | undefined, fractionDigits = 1): string {
  if (value == null || Number.isNaN(value)) return DASH;
  return `${value.toFixed(fractionDigits)}%`;
}

/** Format milliseconds, switching to seconds above 1000ms. */
export function formatLatency(ms: number | null | undefined): string {
  if (ms == null || Number.isNaN(ms)) return DASH;
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

/** Compact integer formatting, e.g. 12500 -> "12.5K". */
export function formatCompact(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return DASH;
  return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(
    value,
  );
}

/** Plain grouped integer, e.g. 12500 -> "12,500". */
export function formatNumber(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return DASH;
  return new Intl.NumberFormat("en").format(value);
}

/** Format a USD cost; uses more precision for tiny amounts. */
export function formatCost(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return DASH;
  if (value === 0) return "$0.00";
  if (value < 0.01) return `$${value.toFixed(4)}`;
  return `$${value.toFixed(2)}`;
}

/** Format a score (0..1) to two decimals, e.g. 0.92 -> "0.92". */
export function formatScore(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return DASH;
  return value.toFixed(2);
}

/** Turn an ENUM_LIKE_TOKEN into "Enum like token". */
export function humanize(value: string): string {
  return value
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/^\w/, (c) => c.toUpperCase());
}

/** Format a signed delta percentage, e.g. -4.2 -> "-4.2%", 0 -> "0%". */
export function formatDelta(value: number | null | undefined, fractionDigits = 1): string {
  if (value == null || Number.isNaN(value)) return DASH;
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(fractionDigits)}%`;
}

// ---------------------------------------------------------------------------
// Severity / confidence presentation — shared by the Phase 4 advisor,
// root-cause, debugger and knowledge surfaces.
// ---------------------------------------------------------------------------
export type Severity = "INFO" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type Confidence = "LOW" | "MEDIUM" | "HIGH";

type BadgeVariant = "default" | "secondary" | "outline" | "success" | "destructive" | "muted";

/** Map a severity to a Badge variant. Unknown values fall back to muted. */
export function severityBadgeVariant(severity: string | null | undefined): BadgeVariant {
  switch (severity) {
    case "CRITICAL":
    case "HIGH":
      return "destructive";
    case "MEDIUM":
      return "default";
    case "LOW":
      return "secondary";
    case "INFO":
      return "muted";
    default:
      return "muted";
  }
}

/** Rank a severity so collections can be ordered most-to-least severe. */
export function severityRank(severity: string | null | undefined): number {
  switch (severity) {
    case "CRITICAL":
      return 4;
    case "HIGH":
      return 3;
    case "MEDIUM":
      return 2;
    case "LOW":
      return 1;
    case "INFO":
      return 0;
    default:
      return -1;
  }
}

/** Map a confidence level to a Badge variant. */
export function confidenceBadgeVariant(confidence: string | null | undefined): BadgeVariant {
  switch (confidence) {
    case "HIGH":
      return "success";
    case "MEDIUM":
      return "default";
    case "LOW":
      return "muted";
    default:
      return "muted";
  }
}
