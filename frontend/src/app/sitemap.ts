import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";
import { DOC_SECTIONS } from "@/lib/docs";

/**
 * Public, crawlable pages only — the application itself is behind authentication.
 *
 * Documentation priority is derived from its section rather than being flat: the pages that define
 * the category and the product carry the most weight, because they are the ones a search engine or
 * a model should surface when someone asks what Broks Forge is.
 */
const SECTION_PRIORITY: Record<string, number> = {
  introduction: 0.9,
  concepts: 0.8,
  capabilities: 0.8,
  comparisons: 0.7,
  workflow: 0.6,
  developer: 0.6,
  reference: 0.6,
  handbook: 0.4,
};

export default function sitemap(): MetadataRoute.Sitemap {
  const docs = DOC_SECTIONS.flatMap((section) =>
    section.docs.map((doc) => ({
      path: `/docs/${doc.slug}`,
      priority: SECTION_PRIORITY[section.id] ?? 0.5,
    })),
  );

  const routes: { path: string; priority: number }[] = [
    { path: "", priority: 1 },
    { path: "/docs", priority: 0.9 },
    ...docs,
    { path: "/login", priority: 0.5 },
    { path: "/register", priority: 0.5 },
    { path: "/forgot-password", priority: 0.3 },
  ];

  const lastModified = new Date();
  return routes.map(({ path, priority }) => ({
    url: `${SITE_URL}${path}`,
    lastModified,
    changeFrequency: "monthly",
    priority,
  }));
}
