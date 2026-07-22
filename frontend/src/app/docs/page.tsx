import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { getAllDocs } from "@/lib/docs";

export const dynamic = "force-static";

export const metadata: Metadata = {
  title: "Docs · Brok's Forge",
  description: "Architecture, engineering rules and API guidelines for Brok's Forge.",
};

export default function DocsIndexPage() {
  const docs = getAllDocs();

  return (
    <div>
      <h1 className="text-3xl font-semibold tracking-tight text-foreground">Documentation</h1>
      <p className="mt-3 max-w-2xl leading-relaxed text-muted-foreground">
        The same engineering docs the project is built from — architecture decisions, project
        rules, security model and API conventions — not a separate marketing copy of them.
      </p>

      <div className="mt-10 grid gap-4 sm:grid-cols-2">
        {docs.map((doc) => (
          <Link
            key={doc.slug}
            href={`/docs/${doc.slug}`}
            className="group rounded-lg border border-border/60 bg-card p-5 transition-colors hover:border-primary/40"
          >
            <div className="flex items-center justify-between">
              <h2 className="font-medium text-foreground">{doc.title}</h2>
              <ArrowRight className="h-4 w-4 text-muted-foreground/50 transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
            </div>
            {doc.description && (
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{doc.description}</p>
            )}
          </Link>
        ))}
      </div>
    </div>
  );
}
