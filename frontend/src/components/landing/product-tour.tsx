"use client";

import Image, { type StaticImageData } from "next/image";
import Link from "next/link";
import { useState } from "react";
import { ArrowRight } from "lucide-react";
import brok from "@/Screenshots/v2-brok.jpg";
import registry from "@/Screenshots/v2-registry.jpg";
import rootCause from "@/Screenshots/v2-root-cause.jpg";
import forgeGraph from "@/Screenshots/v2-forge-graph.jpg";
import aiGit from "@/Screenshots/v2-ai-git.jpg";
import { Reveal } from "./reveal";
import { cn } from "@/lib/utils";

/**
 * The product itself, shown rather than described.
 *
 * Tabs instead of a long scroll of alternating rows: five screens stacked vertically read as a
 * feature brochure, whereas letting someone choose which surface to look at respects P-5 (depth
 * chosen, never forced) and keeps the section to one screen. Each screenshot loads only when its
 * tab is opened, so the section costs one image rather than five.
 *
 * Every caption names the engineering question the surface answers, because that is the axis this
 * product is organised on — not the widgets it contains.
 */
type Surface = {
  id: string;
  tab: string;
  question: string;
  title: string;
  body: string;
  image: StaticImageData;
  alt: string;
  href: string;
};

const SURFACES: Surface[] = [
  {
    id: "brok",
    tab: "Brok",
    question: "What should I know this morning?",
    title: "The record, briefed.",
    body: "Brok opens on standing briefs read straight from the engineering record — what ran, what was promoted, what is failing, what knowledge was derived overnight. Ask it a question and the answer is composed from real rows, each statement declaring how it is known.",
    image: brok,
    alt: "Brok's briefing surface, showing daily, deployment, incident, prompt, evaluation, dataset, knowledge and architecture briefs derived from the engineering record.",
    href: "/docs/brok",
  },
  {
    id: "root-cause",
    tab: "Root Cause Explorer",
    question: "Why did this fail?",
    title: "An investigation, already assembled.",
    body: "Open a failure and the chronology, the cause at four depths, the supporting evidence, the AI Git chain and every earlier failure on the same ground are waiting. You read an investigation instead of running one.",
    image: rootCause,
    alt: "The Root Cause Explorer showing a layered causal investigation of a failed evaluation.",
    href: "/docs/root-cause-explorer",
  },
  {
    id: "registry",
    tab: "Registry",
    question: "What do we actually have?",
    title: "An engineering inventory, not a table.",
    body: "Every artifact and every derived knowledge object in one catalog, each row carrying its health, its evidence and its history — so the list itself tells you where the risk is.",
    image: registry,
    alt: "The Registry listing engineering artifacts alongside derived knowledge objects.",
    href: "/docs/registry",
  },
  {
    id: "forge-graph",
    tab: "Forge Graph",
    question: "What would break if I changed this?",
    title: "The map your architecture diagram never keeps up with.",
    body: "Artifacts and their real relationships, drawn from what the system actually did rather than from a document someone maintained. Blast radius stops being a guess.",
    image: forgeGraph,
    alt: "The Forge Graph showing artifacts and the relationships between them.",
    href: "/docs/forge-graph",
  },
  {
    id: "ai-git",
    tab: "AI Git",
    question: "What changed, and why?",
    title: "Every revision keeps its reason.",
    body: "Promotions, rollbacks and supersessions with the sentence the engineer wrote at the time. When production is running an older revision than the newest one, it is displayed as a rollback rather than left to be discovered.",
    image: aiGit,
    alt: "The AI Git evolution timeline for a prompt, showing revisions with the reason recorded for each.",
    href: "/docs/ai-git",
  },
];

export function ProductTour() {
  const [active, setActive] = useState(0);
  const surface = SURFACES[active];

  return (
    <section id="product" className="border-b border-border/60 py-20 sm:py-24">
      <div className="container">
        <Reveal>
          <p className="text-sm font-medium uppercase tracking-wide text-primary">The product</p>
          <h2 className="mt-3 max-w-3xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            Five surfaces, one engineering record.
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            No surface owns its own data. Each is a different lens on the same record, which is why
            they can never disagree with one another.
          </p>
        </Reveal>

        <Reveal delay={0.1}>
          <div
            role="tablist"
            aria-label="Product surfaces"
            className="mt-10 flex snap-x gap-1.5 overflow-x-auto pb-1"
          >
            {SURFACES.map((sf, i) => (
              <button
                key={sf.id}
                type="button"
                role="tab"
                id={`tour-tab-${sf.id}`}
                aria-selected={i === active}
                aria-controls={`tour-panel-${sf.id}`}
                onClick={() => setActive(i)}
                className={cn(
                  "shrink-0 snap-start rounded-lg border px-3.5 py-2 text-sm font-medium transition-colors",
                  i === active
                    ? "border-primary/50 bg-primary/10 text-foreground"
                    : "border-border/60 text-muted-foreground hover:border-border hover:text-foreground",
                )}
              >
                {sf.tab}
              </button>
            ))}
          </div>
        </Reveal>

        <Reveal delay={0.15}>
          <div
            role="tabpanel"
            id={`tour-panel-${surface.id}`}
            aria-labelledby={`tour-tab-${surface.id}`}
            className="mt-6 grid grid-cols-1 gap-8 lg:grid-cols-[1fr_1.6fr] lg:items-center lg:gap-12"
          >
            <div className="min-w-0">
              <p className="text-sm font-medium text-primary">{surface.question}</p>
              <h3 className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
                {surface.title}
              </h3>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{surface.body}</p>
              <Link
                href={surface.href}
                className="mt-5 inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
              >
                How it works
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>

            {/* Fixed aspect ratio so switching tabs never shifts the page. */}
            <div className="min-w-0 overflow-hidden rounded-xl border border-border bg-[#0b0f14] shadow-lg">
              <div className="flex items-center gap-1.5 border-b border-white/5 px-3 py-2">
                {["#f57","#fb5","#5c8"].map((c) => (
                  <span key={c} className="h-2 w-2 rounded-full" style={{ backgroundColor: c, opacity: 0.5 }} />
                ))}
              </div>
              <div className="relative aspect-[1440/900]">
                <Image
                  key={surface.id}
                  src={surface.image}
                  alt={surface.alt}
                  fill
                  placeholder="blur"
                  // Never priority: this section is well below the fold, so preloading it would
                  // compete with the hero for bandwidth on a phone. Lazy still loads immediately
                  // once a tab is opened, because the image is in the viewport by then.
                  loading="lazy"
                  sizes="(min-width: 1024px) 60vw, 100vw"
                  className="object-cover object-top"
                />
              </div>
            </div>
          </div>
        </Reveal>

        <Reveal delay={0.2}>
          <p className="mt-6 text-xs text-muted-foreground/80">
            Captured from a running instance with seeded data. Nothing here is a mockup.
          </p>
        </Reveal>
      </div>
    </section>
  );
}
