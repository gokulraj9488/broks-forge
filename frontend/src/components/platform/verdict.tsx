"use client";

import Link from "next/link";
import {
  AlertTriangle,
  ArrowUpRight,
  CircleDot,
  HelpCircle,
  Link2,
  ShieldCheck,
  Sparkles,
  XCircle,
} from "lucide-react";
import {
  EPISTEMIC_STYLE,
  VERDICT_STYLE,
  type EpistemicStatus,
  type Provenance,
  type Verdict,
  type VerdictState,
} from "@/lib/verdict";
import { cn } from "@/lib/utils";

const STATE_ICON: Record<VerdictState, typeof ShieldCheck> = {
  healthy: ShieldCheck,
  attention: AlertTriangle,
  risk: AlertTriangle,
  failed: XCircle,
  unknown: HelpCircle,
};

/**
 * The VerdictLine — the most-used component in Broks Forge and the mechanism that makes P-1 (meaning before
 * measurement) enforceable. It states, in one sentence: what happened, why it matters, on what epistemic
 * footing, and where the receipts are.
 *
 * Deliberately, provenance renders in the SAME GLANCE as the claim rather than a click away: the whole point
 * of the product is that its sentences can be checked (Ch 53, "trust as a budget").
 */
export function VerdictLine({
  verdict,
  size = "md",
  className,
  headingLevel,
}: {
  verdict: Verdict;
  size?: "sm" | "md" | "lg";
  className?: string;
  /**
   * Render the headline as a heading instead of a paragraph. Set this only where the verdict is the
   * surface's own title — the Brief, a workspace header — because that headline is then the page's
   * real heading, and a screen reader has nothing else to navigate to.
   */
  headingLevel?: "h1" | "h2";
}) {
  const s = VERDICT_STYLE[verdict.state];
  const Icon = STATE_ICON[verdict.state];
  const text =
    size === "lg" ? "text-lg sm:text-xl" : size === "sm" ? "text-sm" : "text-base";
  const Headline = headingLevel ?? "p";

  return (
    <div className={cn("flex items-start gap-3", className)}>
      <Icon className={cn("mt-0.5 shrink-0", s.fg, size === "lg" ? "h-5 w-5" : "h-4 w-4")} />
      <div className="min-w-0 flex-1 space-y-1">
        <Headline className={cn("font-medium leading-snug text-foreground", text)}>
          {verdict.headline}
          {verdict.status !== "derived" && <EpistemicMark status={verdict.status} className="ml-2 align-middle" />}
        </Headline>
        {verdict.consequence && (
          <p className={cn("text-muted-foreground", size === "lg" ? "text-sm" : "text-xs")}>{verdict.consequence}</p>
        )}
        {verdict.provenance && <ProvenanceNote provenance={verdict.provenance} />}
      </div>
    </div>
  );
}

/**
 * A banner-weight verdict for the top of a surface. Used where the verdict IS the screen's first element —
 * the Brief, a workspace header, an execution result.
 */
export function VerdictBanner({
  verdict,
  action,
  headingLevel,
}: {
  verdict: Verdict;
  action?: React.ReactNode;
  headingLevel?: "h1" | "h2";
}) {
  const s = VERDICT_STYLE[verdict.state];
  return (
    <div className={cn("flex flex-wrap items-start justify-between gap-3 rounded-xl border px-4 py-3.5", s.border, s.bg)}>
      <VerdictLine verdict={verdict} size="lg" className="min-w-0 flex-1 sm:min-w-[16rem]" headingLevel={headingLevel} />
      {action && <div className="flex shrink-0 items-center gap-2">{action}</div>}
    </div>
  );
}

/**
 * L-33: every statement carries exactly one epistemic status, distinguishable at a glance. Derived statements
 * carry no mark (they are the product's default voice); anything less certain is always marked.
 */
export function EpistemicMark({
  status,
  className,
}: {
  status: EpistemicStatus;
  className?: string;
}) {
  const e = EPISTEMIC_STYLE[status];
  const Icon = status === "suggested" ? Sparkles : status === "inferred" ? CircleDot : HelpCircle;
  return (
    <span
      title={e.register}
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide",
        e.border,
        e.fg,
        className,
      )}
    >
      <Icon className="h-2.5 w-2.5" />
      {e.label}
    </span>
  );
}

/** The receipts affordance (P-2). Renders the basis in words and, where one exists, the route to it. */
export function ProvenanceNote({ provenance, className }: { provenance: Provenance; className?: string }) {
  // `truncate` only truncates if the flex item is allowed to shrink below its content width, which
  // needs min-w-0 — a flex item defaults to min-width:auto. Without it a long basis widens the whole
  // page on a phone instead of ellipsing.
  const body = (
    <span className="inline-flex max-w-full items-center gap-1.5">
      <Link2 className="h-3 w-3 shrink-0" />
      <span className="min-w-0 truncate">Derived from {provenance.basis}</span>
      {provenance.href && <ArrowUpRight className="h-3 w-3 shrink-0" />}
    </span>
  );
  return (
    <p className={cn("text-[11px] text-muted-foreground/80", className)}>
      {provenance.href ? (
        <Link href={provenance.href} className="hover:text-foreground hover:underline">
          {body}
        </Link>
      ) : (
        body
      )}
    </p>
  );
}

/** A compact state dot + word. Colour never travels alone (L-20). */
export function VerdictChip({
  state,
  label,
  className,
}: {
  state: VerdictState;
  label?: string;
  className?: string;
}) {
  const s = VERDICT_STYLE[state];
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide",
        s.border,
        s.fg,
        className,
      )}
    >
      <span className={cn("h-1.5 w-1.5 rounded-full bg-current")} />
      {label ?? s.word}
    </span>
  );
}

/**
 * §12.4 — a number displayed prominently must say what it means. This renders consequence as a sentence
 * rather than as a metric ("a change here affects 7 downstream artifacts", not "Impact: 7").
 */
export function ImpactStatement({
  count,
  noun = "downstream artifact",
  verb = "affects",
  href,
}: {
  count: number;
  noun?: string;
  verb?: string;
  href?: string | null;
}) {
  const text =
    count === 0
      ? "Nothing downstream depends on this yet."
      : `A change here ${verb} ${count} ${count === 1 ? noun : noun + "s"}.`;
  const inner = <span className={count > 0 ? "text-foreground" : "text-muted-foreground"}>{text}</span>;
  return (
    <p className="text-xs">
      {href && count > 0 ? (
        <Link href={href} className="hover:underline">
          {inner}
        </Link>
      ) : (
        inner
      )}
    </p>
  );
}
