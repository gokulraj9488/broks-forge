import { Reveal } from "./reveal";

export function WhyWeBuilt() {
  return (
    <section className="border-b border-border/60 py-24">
      <div className="container">
        <div className="grid gap-12 lg:grid-cols-2 lg:gap-16">
          <Reveal>
            <h2 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
              Why we built Brok&apos;s Forge
            </h2>
            <p className="mt-4 leading-relaxed text-muted-foreground">
              Shipping an AI agent to production usually means the actual engineering rigor stops
              at the prompt. There&apos;s no dataset to re-run, no baseline to regress against, no
              record of which model or prompt version produced which result. Every change is an
              experiment nobody can reproduce.
            </p>
            <p className="mt-4 leading-relaxed text-muted-foreground">
              We kept hitting the same wall building agents ourselves: the tooling for testing an
              AI system stayed stuck at the maturity of testing a script by running it once and
              reading the output. Nothing about that scales past the first working demo.
            </p>
          </Reveal>

          <Reveal delay={0.1}>
            <div className="rounded-xl border border-border/60 bg-card p-8">
              <p className="text-sm font-medium uppercase tracking-wide text-muted-foreground">
                What changes
              </p>
              <p className="mt-3 leading-relaxed text-foreground">
                Datasets, prompts and evaluation profiles become versioned records instead of
                throwaway inputs. Every evaluation run is pinned to the exact agent version,
                prompt version and model that produced it. A regression check is a query, not a
                guess. The workflow is the same whether the model behind it is a hosted API or an
                agent you deployed yourself.
              </p>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
