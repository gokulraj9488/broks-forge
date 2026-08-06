/**
 * Canonical site metadata shared by the Metadata API, manifest, robots and
 * sitemap. The public base URL is build-time configurable; it drives absolute
 * URLs for Open Graph, canonical links and the sitemap.
 */
// The fallback is the live production origin on purpose. This value becomes every canonical URL,
// every sitemap entry and every link in llms.txt / llms-full.txt, so a build that loses
// NEXT_PUBLIC_APP_URL should still point at the site that actually exists rather than at a domain
// nothing is served from - a wrong canonical is worse than a missing one.
export const SITE_URL = (process.env.NEXT_PUBLIC_APP_URL ?? "https://broksforge.gokul.quest").replace(/\/+$/, "");

export const SITE_NAME = "Brok's Forge";

export const SITE_TAGLINE = "The AI Engineering Operating System";

export const SITE_DESCRIPTION =
  "Brok's Forge is an AI Engineering Operating System — register, version, evaluate and reason about " +
  "your AI systems, with Brok, the engineering partner that answers from your own engineering record.";
