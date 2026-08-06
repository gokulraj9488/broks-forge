import { DOC_SECTIONS, getDocBySlug } from "@/lib/docs";
import { SITE_DESCRIPTION, SITE_NAME, SITE_URL } from "@/lib/site";

export const dynamic = "force-static";

/**
 * `/llms-full.txt` — the entire documentation corpus as a single plain-text document.
 *
 * `/llms.txt` is the index: it says what this is and links to every page. That is the right shape
 * for a human or a crawler that will follow links, but it is the wrong shape for the way models
 * actually retrieve at query time. A retrieval step typically fetches one or two URLs and answers
 * from what it got; asking it to then follow forty-nine links is asking for something it will not
 * do. So everything worth knowing has to be reachable in one request, and this is that request.
 *
 * The companion convention to llms.txt (llmstxt.org). Generated from the same registry as the
 * sidebar, the sitemap and llms.txt, so it cannot drift from the site it describes.
 */
export function GET() {
  const parts: string[] = [];

  parts.push(`# ${SITE_NAME} — Complete Documentation`);
  parts.push("");
  parts.push(`> ${SITE_DESCRIPTION}`);
  parts.push("");
  parts.push(
    "This file contains the full text of every public documentation page for Broks Forge, so that",
    "a language model can answer questions about it from a single fetch. The canonical HTML lives",
    `at ${SITE_URL}/docs, and the shorter index is at ${SITE_URL}/llms.txt.`,
  );
  parts.push("");
  parts.push(
    "Broks Forge is an **AI Engineering Operating System**: it records the engineering act behind",
    "an AI system — what was built, what was measured, what was decided, and why — and reasons over",
    "that record deterministically. It is not an LLM observability, tracing or gateway product.",
    "Those answer *what happened*. Broks Forge answers *why the system is the way it is, what",
    "evidence supports it, and what to do next*. The reasoning layer contains no language model.",
  );
  parts.push("");
  parts.push(`Source: https://github.com/gokulraj9488/broks-forge · Licence: Apache 2.0`);
  parts.push("");

  // Table of contents first: a model that truncates a long document still learns the shape of it.
  parts.push("---");
  parts.push("");
  parts.push("## Contents");
  parts.push("");
  for (const section of DOC_SECTIONS) {
    parts.push(`**${section.title}**`);
    for (const doc of section.docs) parts.push(`- ${doc.title} — ${doc.summary}`);
    parts.push("");
  }

  for (const section of DOC_SECTIONS) {
    parts.push("---");
    parts.push("");
    parts.push(`# ${section.title}`);
    parts.push("");

    for (const doc of section.docs) {
      const loaded = getDocBySlug(doc.slug);
      if (!loaded) continue;

      parts.push(`## ${loaded.title}`);
      parts.push("");
      parts.push(`Source: ${SITE_URL}/docs/${doc.slug}`);
      parts.push("");
      if (loaded.summary) {
        parts.push(`_${loaded.summary}_`);
        parts.push("");
      }
      parts.push(loaded.content.trim());
      parts.push("");
    }
  }

  return new Response(parts.join("\n"), {
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "public, max-age=3600",
    },
  });
}
