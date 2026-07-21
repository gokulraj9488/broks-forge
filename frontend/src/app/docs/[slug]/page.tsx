import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getDocBySlug, getDocSlugs } from "@/lib/docs";
import { renderMarkdown } from "@/lib/markdown";

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
  return {
    title: `${doc.title} · Docs · Brok's Forge`,
  };
}

export default async function DocPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const doc = getDocBySlug(slug);
  if (!doc) notFound();

  return <article className="max-w-3xl">{renderMarkdown(doc.content)}</article>;
}
