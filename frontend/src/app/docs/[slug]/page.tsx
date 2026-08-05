import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, ArrowRight } from "lucide-react";
import {
  getDocBySlug,
  getDocNeighbours,
  getDocSection,
  getDocSlugs,
} from "@/lib/docs";
import { renderMarkdown } from "@/lib/markdown";
import { SITE_NAME, SITE_URL } from "@/lib/site";

export const dynamic = "force-static";
export const dynamicParams = false;

export function generateStaticParams() {
  return getDocSlugs().map((slug) => ({ slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const doc = getDocBySlug(slug);
  if (!doc) return {};
  const url = `${SITE_URL}/docs/${slug}`;
  return {
    title: doc.title,
    description: doc.summary,
    alternates: { canonical: `/docs/${slug}` },
    openGraph: {
      type: "article",
      title: `${doc.title} · ${SITE_NAME}`,
      description: doc.summary,
      url,
    },
    twitter: {
      card: "summary_large_image",
      title: `${doc.title} · ${SITE_NAME}`,
      description: doc.summary,
    },
  };
}

export default async function DocPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const doc = getDocBySlug(slug);
  if (!doc) notFound();

  const section = getDocSection(slug);
  const { previous, next } = getDocNeighbours(slug);

  /*
   * TechArticle structured data. Search engines and LLM crawlers use this to understand that each
   * page is technical documentation belonging to a named software product — which is the whole
   * point of this phase: the category is new, so the machine-readable description has to be explicit.
   */
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "TechArticle",
    headline: doc.title,
    description: doc.summary,
    url: `${SITE_URL}/docs/${slug}`,
    inLanguage: "en",
    isPartOf: {
      "@type": "WebSite",
      name: SITE_NAME,
      url: SITE_URL,
    },
    about: {
      "@type": "SoftwareApplication",
      name: SITE_NAME,
      applicationCategory: "DeveloperApplication",
      description: "An AI Engineering Operating System.",
    },
    author: { "@type": "Person", name: "Gokulraj" },
  };

  return (
    <div className="max-w-3xl">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      {section && (
        <nav aria-label="Breadcrumb" className="mb-4 text-xs text-muted-foreground">
          <Link href="/docs" className="transition-colors hover:text-foreground">
            Docs
          </Link>
          <span className="px-1.5 text-muted-foreground/50">/</span>
          <Link href={`/docs#${section.id}`} className="transition-colors hover:text-foreground">
            {section.title}
          </Link>
        </nav>
      )}

      <article>{renderMarkdown(doc.content)}</article>

      <nav
        aria-label="Documentation"
        className="mt-16 grid grid-cols-1 gap-3 border-t border-border/60 pt-8 sm:grid-cols-2"
      >
        {previous ? (
          <Link
            href={`/docs/${previous.slug}`}
            className="group rounded-lg border border-border/60 p-4 transition-colors hover:border-primary/40"
          >
            <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <ArrowLeft className="h-3 w-3" />
              Previous
            </span>
            <span className="mt-1 block font-medium text-foreground">{previous.title}</span>
          </Link>
        ) : (
          <span />
        )}
        {next && (
          <Link
            href={`/docs/${next.slug}`}
            className="group rounded-lg border border-border/60 p-4 text-right transition-colors hover:border-primary/40 sm:col-start-2"
          >
            <span className="flex items-center justify-end gap-1.5 text-xs text-muted-foreground">
              Next
              <ArrowRight className="h-3 w-3" />
            </span>
            <span className="mt-1 block font-medium text-foreground">{next.title}</span>
          </Link>
        )}
      </nav>
    </div>
  );
}
