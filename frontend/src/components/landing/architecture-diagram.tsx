import { ArrowDown, BarChart3, FileOutput, FlaskConical, Layout, Server } from "lucide-react";
import { Reveal } from "./reveal";

const NODES = [
  { icon: Layout, label: "React", sub: "Next.js frontend" },
  { icon: Server, label: "Spring Boot", sub: "Modular monolith API" },
  { icon: FlaskConical, label: "Evaluation Engine", sub: "Job → Run → Result" },
];

const PROVIDERS = ["Claude", "GPT", "Gemini", "Groq", "OpenRouter"];

const TAIL_NODES = [
  { icon: BarChart3, label: "Metrics", sub: "Prometheus" },
  { icon: FileOutput, label: "Reports", sub: "JSON / CSV / HTML" },
];

export function ArchitectureDiagram() {
  return (
    <section className="border-b border-border/60 py-24">
      <div className="container">
        <Reveal className="mx-auto max-w-2xl text-center">
          <h2 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            Architecture
          </h2>
          <p className="mt-4 text-lg text-muted-foreground">
            A modular monolith, not a tangle of microservices.
          </p>
        </Reveal>

        <div className="mx-auto mt-14 flex max-w-md flex-col items-center gap-2">
          {NODES.map((node, i) => (
            <div key={node.label} className="flex flex-col items-center gap-2">
              <Reveal delay={i * 0.08} className="w-full">
                <div className="flex w-64 items-center gap-3 rounded-lg border border-border/60 bg-card px-4 py-3 font-mono text-sm shadow-sm">
                  <node.icon className="h-4 w-4 shrink-0 text-primary" />
                  <div>
                    <div className="font-medium text-foreground">{node.label}</div>
                    <div className="text-xs text-muted-foreground">{node.sub}</div>
                  </div>
                </div>
              </Reveal>
              <ArrowDown className="h-4 w-4 text-muted-foreground/50" aria-hidden="true" />
            </div>
          ))}

          {/* Fan-out: the evaluation engine dispatches to whichever provider the job pins. */}
          <Reveal delay={NODES.length * 0.08} className="w-full">
            <div className="flex flex-wrap items-center justify-center gap-2 rounded-lg border border-dashed border-border/60 bg-secondary/30 px-4 py-3">
              {PROVIDERS.map((provider) => (
                <span
                  key={provider}
                  className="rounded-md border border-border/60 bg-card px-2.5 py-1 font-mono text-xs text-foreground"
                >
                  {provider}
                </span>
              ))}
            </div>
          </Reveal>
          <ArrowDown className="h-4 w-4 text-muted-foreground/50" aria-hidden="true" />

          {TAIL_NODES.map((node, i) => (
            <div key={node.label} className="flex flex-col items-center gap-2">
              <Reveal delay={(NODES.length + 1 + i) * 0.08} className="w-full">
                <div className="flex w-64 items-center gap-3 rounded-lg border border-border/60 bg-card px-4 py-3 font-mono text-sm shadow-sm">
                  <node.icon className="h-4 w-4 shrink-0 text-primary" />
                  <div>
                    <div className="font-medium text-foreground">{node.label}</div>
                    <div className="text-xs text-muted-foreground">{node.sub}</div>
                  </div>
                </div>
              </Reveal>
              {i < TAIL_NODES.length - 1 && (
                <ArrowDown className="h-4 w-4 text-muted-foreground/50" aria-hidden="true" />
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
