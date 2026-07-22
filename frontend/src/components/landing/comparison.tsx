import { ArrowRight } from "lucide-react";
import { Reveal } from "./reveal";

const ROWS = [
  { before: "Manual prompt testing", after: "Automated evaluations" },
  { before: "Spreadsheet benchmarks", after: "Unified benchmark suites" },
  { before: "Guessing why it failed", after: "Root cause analysis" },
  { before: "Scattered tools per model", after: "One engineering platform" },
  { before: "One-off experiments", after: "Versioned, reproducible runs" },
];

export function Comparison() {
  return (
    <section className="border-b border-border/60 py-24">
      <div className="container">
        <Reveal className="mx-auto max-w-2xl text-center">
          <h2 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            The workflow this replaces
          </h2>
        </Reveal>

        <Reveal delay={0.1} className="mx-auto mt-16 max-w-2xl">
          <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-x-4 gap-y-0 sm:gap-x-8">
            <p className="pb-4 text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Traditional workflow
            </p>
            <span />
            <p className="pb-4 text-right text-xs font-medium uppercase tracking-wide text-primary sm:text-left">
              Brok&apos;s Forge
            </p>

            {ROWS.map((row) => (
              <div key={row.before} className="contents">
                <div className="border-t border-border/60 py-5 text-[15px] text-muted-foreground">
                  {row.before}
                </div>
                <div className="border-t border-border/60 py-5">
                  <ArrowRight className="h-4 w-4 text-muted-foreground/30" />
                </div>
                <div className="border-t border-border/60 py-5 text-right text-[15px] font-medium tracking-tight text-foreground sm:text-left">
                  {row.after}
                </div>
              </div>
            ))}
          </div>
        </Reveal>
      </div>
    </section>
  );
}
