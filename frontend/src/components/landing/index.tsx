import Link from "next/link";
import { Logo } from "@/components/brand/logo";
import { EngineeringBackground } from "./engineering-background";
import { LandingNav } from "./landing-nav";
import { Hero } from "./hero";
import { ProblemSolution } from "./problem-solution";
import { FiveLayers } from "./five-layers";
import { CapabilitySections } from "./capability-sections";
import { EngineeringJourney } from "./engineering-journey";
import { ScreenshotShowcase } from "./screenshot-showcase";
import { ArchitectureDiagram } from "./architecture-diagram";
import { Comparison } from "./comparison";
import { WhyTeams } from "./why-teams";
import { LandingFaq } from "./landing-faq";
import { FinalCta } from "./final-cta";
import { LandingStructuredData } from "./structured-data";

/**
 * The public landing page.
 *
 * The order is an argument, read top to bottom: state the category, show the problem it exists for,
 * show the solution, show the architecture that makes it possible, show each capability as the
 * artifact it produces, show the loop, show the product, be honest about the competitive landscape,
 * say who it is for, answer the obvious questions, then invite.
 *
 * The constitutional test for this page: a first-time visitor must infer "AI Engineering Operating
 * System" within thirty seconds, without documentation.
 */
const FOOTER_LINKS: { heading: string; links: { label: string; href: string; external?: boolean }[] }[] = [
  {
    heading: "Product",
    links: [
      { label: "What is Broks Forge?", href: "/docs/what-is-broks-forge" },
      { label: "The Five Layers", href: "/docs/the-five-layers" },
      { label: "Brok", href: "/docs/brok" },
      { label: "Root Cause Explorer", href: "/docs/root-cause-explorer" },
      { label: "AI Git", href: "/docs/ai-git" },
    ],
  },
  {
    heading: "Learn",
    links: [
      { label: "Getting Started", href: "/docs/getting-started" },
      { label: "Core Concepts", href: "/docs/core-concepts" },
      { label: "Engineering Workflow", href: "/docs/engineering-workflow" },
      { label: "Examples", href: "/docs/examples" },
      { label: "Glossary", href: "/docs/glossary" },
    ],
  },
  {
    heading: "Compare",
    links: [
      { label: "Overview", href: "/docs/comparisons" },
      { label: "vs LangFuse", href: "/docs/vs-langfuse" },
      { label: "vs LangSmith", href: "/docs/vs-langsmith" },
      { label: "vs Promptfoo", href: "/docs/vs-promptfoo" },
      { label: "vs Weights & Biases", href: "/docs/vs-weights-and-biases" },
    ],
  },
  {
    heading: "Develop",
    links: [
      { label: "Architecture", href: "/docs/architecture" },
      { label: "REST API", href: "/docs/rest-api" },
      { label: "Data Model", href: "/docs/data-model" },
      { label: "Extension Points", href: "/docs/extension-points" },
      { label: "GitHub", href: "https://github.com/gokulraj9488/broks-forge", external: true },
    ],
  },
];

export function LandingPage() {
  return (
    <div className="flex min-h-dvh flex-col">
      <LandingStructuredData />
      <EngineeringBackground />
      <LandingNav />
      <main className="flex-1">
        <Hero />
        <ProblemSolution />
        <FiveLayers />
        <CapabilitySections />
        <EngineeringJourney />
        <ScreenshotShowcase />
        <ArchitectureDiagram />
        <Comparison />
        <WhyTeams />
        <LandingFaq />
        <FinalCta />
      </main>

      <footer className="border-t border-border/60 py-12">
        <div className="container">
          <div className="grid grid-cols-1 gap-10 sm:grid-cols-2 lg:grid-cols-5">
            <div className="lg:col-span-1">
              <Logo />
              <p className="mt-3 max-w-xs text-xs leading-relaxed text-muted-foreground">
                An AI Engineering Operating System. Open source under Apache 2.0.
              </p>
            </div>

            {FOOTER_LINKS.map((group) => (
              <div key={group.heading}>
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground">
                  {group.heading}
                </p>
                <ul className="mt-3 space-y-2">
                  {group.links.map((link) => (
                    <li key={link.href}>
                      <Link
                        href={link.href}
                        target={link.external ? "_blank" : undefined}
                        rel={link.external ? "noopener noreferrer" : undefined}
                        className="text-xs text-muted-foreground transition-colors hover:text-foreground"
                      >
                        {link.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <div className="mt-10 flex flex-col items-center justify-between gap-3 border-t border-border/60 pt-6 sm:flex-row">
            <p className="text-xs text-muted-foreground">
              &copy; {new Date().getFullYear()} Brok&apos;s Forge. Apache 2.0.
            </p>
            <p className="text-xs text-muted-foreground">
              Built by{" "}
              <Link
                href="https://gokul.quest"
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-foreground"
              >
                Gokulraj
              </Link>
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
