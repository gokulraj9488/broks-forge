"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Reveal } from "./reveal";
import { cn } from "@/lib/utils";

/**
 * The four flagship capabilities, each shown as *what it produces* rather than as a feature bullet.
 *
 * Every panel is a faithful, simplified rendering of the real output — a Brok answer, an
 * investigation's causal layers, an AI Git timeline, a derived knowledge chain. Showing the artifact
 * is more honest and more persuasive than describing it, and it teaches the product's vocabulary
 * before a visitor ever signs in.
 */
function Section({
  id,
  eyebrow,
  title,
  body,
  href,
  linkLabel,
  reverse,
  children,
}: {
  id: string;
  eyebrow: string;
  title: string;
  body: string;
  href: string;
  linkLabel: string;
  reverse?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div id={id} className="scroll-mt-24 border-b border-border/60 py-20 sm:py-24">
      <div className="container grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
        <Reveal className={cn(reverse && "lg:order-2")}>
          <p className="text-sm font-medium uppercase tracking-wide text-primary">{eyebrow}</p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            {title}
          </h2>
          <p className="mt-4 text-base leading-relaxed text-muted-foreground">{body}</p>
          <Link
            href={href}
            className="mt-6 inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
          >
            {linkLabel}
            <ArrowRight className="h-4 w-4" />
          </Link>
        </Reveal>
        <Reveal delay={0.1} className={cn("min-w-0", reverse && "lg:order-1")}>
          {children}
        </Reveal>
      </div>
    </div>
  );
}

function Panel({ children, label }: { children: React.ReactNode; label: string }) {
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm" aria-hidden="true">
      <p className="mb-3 text-[10px] uppercase tracking-wide text-muted-foreground/70">{label}</p>
      {children}
    </div>
  );
}

export function CapabilitySections() {
  return (
    <>
      {/* ---------------------------------------------------------------- Brok */}
      <Section
        id="brok"
        eyebrow="Brok"
        title="An engineering partner, not a chatbot."
        body="Ask why something failed, whether to promote it, or what the team should do next — and get
              an answer read from your own engineering record. There is no language model: Brok resolves
              your question to one of 25 engineering intents and composes the answer from real rows. Ask
              it something the record cannot support and it refuses, then offers what it can answer."
        href="/docs/brok"
        linkLabel="How Brok works"
      >
        <Panel label="Ask Brok · Should I promote it?">
          <div className="rounded-lg border border-zinc-500/30 bg-zinc-500/5 p-3">
            <p className="text-sm font-medium text-foreground">
              Nothing has measured v4, so promoting it would be an act of faith.
            </p>
            <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">
              A promotion with no evidence behind it cannot be defended later and cannot be safely
              reversed either.
            </p>
          </div>
          <div className="mt-3 space-y-1.5">
            {[
              ["Derived", "No evaluation has run against Support Prompt since v4 was created."],
              ["Inferred", "The failures read as infrastructure rather than quality."],
            ].map(([status, text]) => (
              <div key={text} className="flex gap-2 text-xs">
                <span
                  className={cn(
                    "shrink-0 rounded px-1.5 py-0.5 text-[9px] uppercase tracking-wide",
                    status === "Derived"
                      ? "bg-muted text-muted-foreground"
                      : "bg-amber-500/10 text-amber-500",
                  )}
                >
                  {status}
                </span>
                <span className="min-w-0 flex-1 leading-snug text-foreground/85">{text}</span>
              </div>
            ))}
          </div>
          <p className="mt-3 border-t border-border/60 pt-2.5 text-[11px] text-muted-foreground">
            Investigated 4 evaluations · 2 AI Git revisions · engineering memory
          </p>
        </Panel>
      </Section>

      {/* -------------------------------------------------- Root Cause Explorer */}
      <Section
        id="root-cause"
        eyebrow="Root Cause Explorer"
        title="When you ask why, you get an investigation."
        body="Open a failure and everything the record holds about it is already assembled: a dated
              chronology of the engineering that led there, the cause at four depths, the evidence and
              AI Git chains, the engineering memory, and every earlier failure on the same ground. No
              hunting across five pages."
        href="/docs/root-cause-explorer"
        linkLabel="How the Explorer works"
        reverse
      >
        <Panel label="Investigation · Checkout Quality #2">
          <div className="space-y-2">
            {[
              { l: "Immediate", t: "The agent's credentials are being rejected", c: "border-l-rose-500/60", m: "6 of 8 sampled runs returned HTTP 401." },
              { l: "Contributing", t: "Every failing run reached the same provider", c: "border-l-amber-500/50", m: "Inferred · likely" },
              { l: "Historical", t: "This has happened before — 19 days ago", c: "border-l-sky-500/50", m: "Identical recorded cause." },
              { l: "Related change", t: "Credentials rotated 3 hours before this ran", c: "border-l-violet-500/50", m: "Proximity, not proof." },
            ].map((row, i) => (
              <div
                key={row.l}
                className={cn(
                  "rounded-lg border border-l-2 border-border/60 bg-background p-2.5",
                  row.c,
                  i > 0 && "ml-3",
                )}
              >
                <p className="text-[9px] uppercase tracking-wide text-muted-foreground/70">
                  {row.l}
                </p>
                <p className="mt-0.5 text-xs font-medium leading-snug text-foreground">{row.t}</p>
                <p className="mt-0.5 text-[11px] text-muted-foreground">{row.m}</p>
              </div>
            ))}
          </div>
        </Panel>
      </Section>

      {/* -------------------------------------------------------------- AI Git */}
      <Section
        id="ai-git"
        eyebrow="AI Git"
        title="Version control for engineering reasoning."
        body="Not source control. AI Git answers engineering questions: what was promoted, why, what it
              replaced, what evidence covered it — and whether production is running the newest revision
              or an older one somebody rolled back to at 2am. The reason you record becomes Engineering
              Memory, recalled verbatim forever."
        href="/docs/ai-git"
        linkLabel="How AI Git works"
      >
        <Panel label="Support Prompt · deployment timeline">
          <div className="mb-3 rounded-lg border border-amber-500/30 bg-amber-500/5 px-3 py-2">
            <p className="text-xs text-foreground">
              <strong>Rolled back.</strong> Production is on v3 even though v4 exists.
            </p>
          </div>
          <ol className="relative space-y-3 border-l border-border pl-4">
            {[
              { v: "v4", s: "Rolled past", r: "Trimmed the system preamble.", dot: "bg-amber-400" },
              { v: "v3", s: "Current production", r: "Softer tone after complaints.", dot: "bg-emerald-400" },
              { v: "v2", s: "Superseded", r: "Added the refusal instruction.", dot: "bg-zinc-500" },
            ].map((r) => (
              <li key={r.v} className="relative">
                <span
                  className={cn(
                    "absolute -left-[1.32rem] top-1 h-2.5 w-2.5 rounded-full border-2 border-background",
                    r.dot,
                  )}
                />
                <div className="flex flex-wrap items-baseline gap-2">
                  <span className="text-xs font-semibold text-foreground">{r.v}</span>
                  <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
                    {r.s}
                  </span>
                </div>
                <p className="mt-0.5 border-l-2 border-border pl-2 text-[11px] italic text-muted-foreground">
                  “{r.r}”
                </p>
              </li>
            ))}
          </ol>
        </Panel>
      </Section>

      {/* ------------------------------------------- Engineering Intelligence */}
      <Section
        id="intelligence"
        eyebrow="Engineering Intelligence"
        title="The reasoning layer nobody has to write."
        body="Observations, claims, decisions, evidence and knowledge — all derived from real engineering
              work. Promoting a version is a decision; running an evaluation is evidence. Knowledge exists
              only where both are genuinely present, which is why it can be trusted, and why the platform
              can also tell you which of your decisions have nothing behind them."
        href="/docs/engineering-intelligence"
        linkLabel="How Engineering Intelligence works"
        reverse
      >
        <Panel label="Derived from real work">
          <div className="space-y-2 text-xs">
            {[
              { k: "Observation", v: "Checkout Quality measured Refund Agent: 2 of 2 items failed." },
              { k: "Decision", v: "Support Prompt v3 promoted — “Softer tone after complaints.”" },
              { k: "Evidence", v: "4 evaluations cover the promoted revision." },
              { k: "Knowledge", v: "Support Prompt's canonical revision is v3, backed by 4 evaluations." },
            ].map((row, i) => (
              <div key={row.k} className="flex gap-2.5">
                <div className="flex w-24 shrink-0 flex-col items-start">
                  <span className="rounded bg-muted px-1.5 py-0.5 text-[9px] uppercase tracking-wide text-muted-foreground">
                    {row.k}
                  </span>
                  {i < 3 && <span className="ml-3 mt-1 h-3 w-px bg-border" />}
                </div>
                <span className="min-w-0 flex-1 leading-snug text-foreground/85">{row.v}</span>
              </div>
            ))}
          </div>
          <div className="mt-3 rounded-lg border border-orange-500/30 bg-orange-500/5 px-3 py-2">
            <p className="text-[11px] leading-snug text-foreground">
              <strong>2 decisions have no evidence behind them.</strong> A promotion nobody measured
              is a position carried on faith.
            </p>
          </div>
        </Panel>
      </Section>

      {/* --------------------------------------------------------- Forge Graph */}
      <Section
        id="forge-graph"
        eyebrow="Forge Graph"
        title="The architecture diagram that cannot go stale."
        body="Every architecture document is out of date the week after it is written, because keeping it
              true is manual. The Forge Graph is not maintained — it is the relationships the system
              actually recorded while you worked. Ask what depends on a dataset before you regenerate it
              and the blast radius is a fact, not a recollection. Narrow the same map to one run and it
              becomes the Execution Graph; narrow it to one failure and it becomes the Failure Graph."
        href="/docs/forge-graph"
        linkLabel="How the Forge Graph works"
      >
        <Panel label="Blast radius · Support Dataset v2">
          <div className="rounded-lg border border-border/60 bg-background p-3">
            <p className="text-xs font-medium text-foreground">
              Changing this dataset touches 5 artifacts.
            </p>
            <p className="mt-1 text-[11px] text-muted-foreground">
              Derived from recorded relationships, not a declared manifest.
            </p>
          </div>
          <div className="mt-3 space-y-1.5">
            {[
              { d: 0, n: "Support Dataset v2", m: "the change", c: "text-foreground" },
              { d: 1, n: "Checkout Quality", m: "evaluates it", c: "text-foreground/85" },
              { d: 1, n: "Refund Agent", m: "measured by it", c: "text-foreground/85" },
              { d: 2, n: "Support Prompt v3", m: "evidence would be invalidated", c: "text-amber-500" },
              { d: 2, n: "2 knowledge objects", m: "would lose their basis", c: "text-amber-500" },
            ].map((r) => (
              <div
                key={r.n}
                className="flex items-center gap-2 text-xs"
                style={{ paddingLeft: `${r.d * 14}px` }}
              >
                <span className="h-1 w-1 shrink-0 rounded-full bg-muted-foreground/50" />
                <span className={cn("min-w-0 truncate font-medium", r.c)}>{r.n}</span>
                <span className="min-w-0 flex-1 truncate text-[11px] text-muted-foreground">
                  {r.m}
                </span>
              </div>
            ))}
          </div>
        </Panel>
      </Section>
    </>
  );
}
