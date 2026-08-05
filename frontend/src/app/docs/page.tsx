import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { getDocSections } from "@/lib/docs";
import { SITE_NAME, SITE_URL } from "@/lib/site";

export const dynamic = "force-static";

const DESCRIPTION =
  "Complete documentation for Broks Forge, an AI Engineering Operating System: core concepts, " +
  "Engineering Intelligence, AI Git, the Forge Graph, Brok, the Root Cause Explorer, comparisons " +
  "with adjacent tools, and the developer reference.";

export const metadata: Metadata = {
  title: "Documentation",
  description: DESCRIPTION,
  alternates: { canonical: "/docs" },
  openGraph: {
    title: `Documentation · ${SITE_NAME}`,
    description: DESCRIPTION,
    url: `${SITE_URL}/docs`,
  },
};

export default function DocsIndexPage() {
  const sections = getDocSections();

  return (
    <div>
      <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
        Documentation
      </h1>
      <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
        Everything needed to understand Broks Forge without reading the source: what an AI
        Engineering Operating System is, how each capability works, how it compares with adjacent
        tools, and how to build on it.
      </p>

      <div className="mt-6 flex flex-wrap gap-3">
        <Link
          href="/docs/what-is-broks-forge"
          className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90"
        >
          Start here
          <ArrowRight className="h-4 w-4" />
        </Link>
        <Link
          href="/docs/getting-started"
          className="inline-flex items-center gap-1.5 rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground transition-colors hover:border-primary/50"
        >
          Run it locally
        </Link>
      </div>

      <div className="mt-14 space-y-12">
        {sections.map((section) => (
          <section key={section.id} id={section.id} className="scroll-mt-24">
            <h2 className="text-lg font-semibold tracking-tight text-foreground">
              {section.title}
            </h2>
            <p className="mt-1 max-w-2xl text-sm leading-relaxed text-muted-foreground">
              {section.blurb}
            </p>

            <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2">
              {section.docs.map((doc) => (
                <Link
                  key={doc.slug}
                  href={`/docs/${doc.slug}`}
                  className="group rounded-lg border border-border/60 bg-card p-4 transition-colors hover:border-primary/40"
                >
                  <div className="flex items-start justify-between gap-3">
                    <h3 className="font-medium leading-snug text-foreground">{doc.title}</h3>
                    <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground/50 transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
                  </div>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {doc.summary}
                  </p>
                </Link>
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
