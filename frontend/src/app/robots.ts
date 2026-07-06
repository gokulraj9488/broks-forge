import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      // Authenticated application areas carry no crawlable content.
      disallow: [
        "/dashboard",
        "/organizations",
        "/projects",
        "/agents",
        "/datasets",
        "/prompts",
        "/evaluations",
        "/benchmarks",
        "/analytics",
        "/insights",
        "/advisor",
        "/knowledge",
        "/settings",
        "/profile",
        "/help",
        "/about",
        "/verify-email",
        "/reset-password",
        "/change-password",
      ],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
