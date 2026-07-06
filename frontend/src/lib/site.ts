/**
 * Canonical site metadata shared by the Metadata API, manifest, robots and
 * sitemap. The public base URL is build-time configurable; it drives absolute
 * URLs for Open Graph, canonical links and the sitemap.
 */
export const SITE_URL = (process.env.NEXT_PUBLIC_APP_URL ?? "https://broksforge.dev").replace(/\/+$/, "");

export const SITE_NAME = "Brok's Forge";

export const SITE_TAGLINE = "The Engineering Platform for AI Agents";

export const SITE_DESCRIPTION =
  "Brok's Forge is the engineering platform for AI agents — register, version, secure credentials, " +
  "evaluate against real datasets, benchmark variants, catch regressions and operate agents at scale.";
