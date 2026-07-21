import Link from "next/link";
import { Logo } from "@/components/brand/logo";
import { LandingNav } from "./landing-nav";
import { Hero } from "./hero";
import { TechMarquee } from "./marquee";
import { WhyWeBuilt } from "./why-we-built";
import { Comparison } from "./comparison";
import { ScreenshotShowcase } from "./screenshot-showcase";
import { WorkflowTimeline } from "./workflow-timeline";
import { FeatureGrid } from "./feature-sections";
import { ArchitectureDiagram } from "./architecture-diagram";
import { LiveDemo } from "./live-demo";
import { FinalCta } from "./final-cta";

export function LandingPage() {
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <LandingNav />
      <main className="flex-1">
        <Hero />
        <TechMarquee />
        <WhyWeBuilt />
        <Comparison />
        <ScreenshotShowcase />
        <WorkflowTimeline />
        <FeatureGrid />
        <ArchitectureDiagram />
        <LiveDemo />
        <FinalCta />
      </main>
      <footer className="border-t border-border/60 py-10">
        <div className="container flex flex-col items-center justify-between gap-4 sm:flex-row">
          <Logo />
          <p className="text-xs text-muted-foreground">
            &copy; {new Date().getFullYear()} Brok&apos;s Forge. Open source under Apache 2.0.
          </p>
          <div className="flex items-center gap-4 text-xs text-muted-foreground">
            <Link href="https://github.com/your-org/broks-forge" target="_blank" rel="noopener noreferrer" className="hover:text-foreground">
              GitHub
            </Link>
            <Link href="/docs" className="hover:text-foreground">
              Docs
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
