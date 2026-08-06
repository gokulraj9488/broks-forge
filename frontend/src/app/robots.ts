import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

/**
 * The assistant crawlers, named explicitly.
 *
 * `User-agent: *` already permits them, so this grants no new access. It is here because the
 * default for these agents is increasingly to be blocked, and an explicit allow is an unambiguous
 * statement of intent rather than a silent inheritance — for the operators of these crawlers, and
 * for whoever edits this file next and would otherwise have to guess whether the omission was
 * deliberate. Broks Forge wants to be read, quoted and cited by assistants: the whole public site
 * exists to explain a category that a model with no prior will otherwise guess wrong.
 */
const ASSISTANT_CRAWLERS = [
  "GPTBot", // OpenAI, training
  "OAI-SearchBot", // OpenAI, ChatGPT search index
  "ChatGPT-User", // OpenAI, live fetch on a user's behalf
  "ClaudeBot", // Anthropic, crawling
  "Claude-User", // Anthropic, live fetch on a user's behalf
  "Claude-SearchBot", // Anthropic, search index
  "anthropic-ai",
  "PerplexityBot",
  "Perplexity-User",
  "Google-Extended", // Gemini / Vertex grounding
  "Applebot-Extended",
  "cohere-ai",
  "Meta-ExternalAgent",
  "CCBot", // Common Crawl, which many models are trained from
];

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      ...ASSISTANT_CRAWLERS.map((userAgent) => ({ userAgent, allow: "/" })),
      {
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
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
