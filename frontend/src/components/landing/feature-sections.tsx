import {
  BarChart3,
  Database,
  FileText,
  FlaskConical,
  FolderKanban,
  Gauge,
  History,
  Network,
  Trophy,
} from "lucide-react";
import { Reveal } from "./reveal";

const FEATURE_GRID = [
  { icon: FolderKanban, label: "Projects" },
  { icon: Database, label: "Datasets" },
  { icon: FileText, label: "Prompt Library" },
  { icon: Trophy, label: "Benchmarks" },
  { icon: FlaskConical, label: "AI Judge" },
  { icon: Network, label: "Root Cause Analysis" },
  { icon: History, label: "Version History" },
  { icon: BarChart3, label: "Reports" },
  { icon: Gauge, label: "Observability" },
];

export function FeatureGrid() {
  return (
    <section id="features" className="border-b border-border/60 py-24">
      <div className="container">
        <Reveal className="mx-auto max-w-2xl text-center">
          <h2 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            Everything an AI engineering team needs
          </h2>
        </Reveal>

        <div className="mt-14 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-3">
          {FEATURE_GRID.map((item, i) => (
            <Reveal key={item.label} delay={i * 0.04}>
              <div className="group flex items-center gap-3 rounded-lg border border-border/60 bg-card p-4 transition-all duration-200 hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-sm">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-secondary text-foreground transition-colors group-hover:bg-primary/10 group-hover:text-primary">
                  <item.icon className="h-4 w-4" />
                </div>
                <span className="text-sm font-medium text-foreground">{item.label}</span>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
