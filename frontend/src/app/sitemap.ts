import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";
import { getDocSlugs } from "@/lib/docs";

/** Public, crawlable pages only (the app itself is behind authentication). */
export default function sitemap(): MetadataRoute.Sitemap {
  const routes: { path: string; priority: number }[] = [
    { path: "", priority: 1 },
    { path: "/docs", priority: 0.8 },
    ...getDocSlugs().map((slug) => ({ path: `/docs/${slug}`, priority: 0.6 })),
    { path: "/login", priority: 0.7 },
    { path: "/register", priority: 0.7 },
    { path: "/forgot-password", priority: 0.4 },
  ];
  return routes.map(({ path, priority }) => ({
    url: `${SITE_URL}${path}`,
    changeFrequency: "monthly",
    priority,
  }));
}
