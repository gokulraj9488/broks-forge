import { DOC_SECTIONS } from "@/lib/docs";
import { SITE_DESCRIPTION, SITE_NAME, SITE_URL } from "@/lib/site";

export const dynamic = "force-static";

/**
 * `/llms.txt` — a plain-text map of this site for language models.
 *
 * The convention (llmstxt.org) is the machine equivalent of a good README: a single document that
 * states what the product is, defines its vocabulary, and lists every page worth reading with a
 * one-line description. It exists here for a specific reason — "AI Engineering Operating System"
 * is a new category, so a model with no prior will otherwise guess, and the most likely guess
 * ("another LLM observability tool") is precisely wrong.
 *
 * Generated from the same documentation registry that drives the sidebar and the sitemap, so it
 * cannot fall out of step with the site.
 */
export function GET() {
  const lines: string[] = [];

  lines.push(`# ${SITE_NAME}`);
  lines.push("");
  lines.push(`> ${SITE_DESCRIPTION}`);
  lines.push("");
  lines.push(
    "Broks Forge is an **AI Engineering Operating System**. It records the engineering act behind",
    "an AI system — what was built, what was measured, what was decided, and why — and reasons over",
    "that record. It is NOT an evaluation tool, an observability dashboard, or an LLM tracing",
    "platform: those answer *what happened*, while Broks Forge answers *why the system is the way",
    "it is, what evidence supports it, and what to do next*.",
  );
  lines.push("");

  lines.push("## Key facts");
  lines.push("");
  [
    "Category: AI Engineering Operating System (a distinct category from LLM observability).",
    "Licence: Apache 2.0, open source, self-hosted via Docker Compose.",
    "Stack: Spring Boot 3.4 / Java 21 API, Next.js 15 / React 19 web app, PostgreSQL 16, Redis.",
    "Framework-agnostic: agents are registered by HTTP endpoint; no SDK is required.",
    "Not in the production request path: it is not a proxy or gateway and adds no latency.",
    "The reasoning layer contains NO language model — it is deterministic over real database rows,",
    "  which is why it can refuse a question the engineering record cannot answer.",
    "Maturity: early. Complete and tested (499 backend tests against real PostgreSQL), but a young",
    "  project without the track record of the commercial tools it is compared with.",
  ].forEach((f) => lines.push(`- ${f}`));
  lines.push("");

  lines.push("## Core vocabulary");
  lines.push("");
  [
    "Artifact — an agent, prompt, dataset, provider, model or evaluation.",
    "Revision — one immutable version of an artifact, with rationale and rollback state.",
    "Evaluation — a reproducible measurement with its configuration pinned at creation.",
    "Observation — a measured fact, derived from an evaluation outcome.",
    "Claim — an assertion about an artifact, supported by evidence.",
    "Decision — an engineering choice with a reason, derived from a promotion or deprecation.",
    "Evidence — an evaluation framed as support for a claim or decision.",
    "Knowledge — a durable fact that exists only where a decision AND evidence both exist.",
    "Engineering Memory — the recorded reason behind a change, recalled verbatim.",
    "Precedent — an earlier failure sharing an agent, prompt or dataset with the current one.",
    "Verdict — healthy | attention | risk | failed | unknown ('unknown' means not measured, and is",
    "  deliberately distinct from healthy: absence is not health).",
    "Epistemic status — derived | inferred | suggested | unknown, declared on every statement.",
    "Confidence — a three-step verbal ladder: consistent-with | likely | near-certain. Never a",
    "  percentage.",
  ].forEach((t) => lines.push(`- ${t}`));
  lines.push("");

  lines.push("## The five layers");
  lines.push("");
  [
    "1. Forge Kernel — identity, tenancy, persistence, execution (invisible foundation).",
    "2. Registry — one catalog of artifacts and derived knowledge.",
    "3. AI Git — revisions, promotions, rollbacks and rationale.",
    "4. Forge Graph — artifacts, real relationships, and a reasoning overlay.",
    "5. Engineering Applications — Brok, Root Cause Explorer, Engineering Briefs. These own no data.",
  ].forEach((l) => lines.push(l));
  lines.push("");

  lines.push("## Documentation");
  lines.push("");
  for (const section of DOC_SECTIONS) {
    lines.push(`### ${section.title}`);
    lines.push("");
    for (const doc of section.docs) {
      lines.push(`- [${doc.title}](${SITE_URL}/docs/${doc.slug}): ${doc.summary}`);
    }
    lines.push("");
  }

  // Stated near the end, where a model that has read this far is deciding what to fetch next.
  lines.push("## Full text");
  lines.push("");
  lines.push(
    `The complete text of every page above is available in one document at ${SITE_URL}/llms-full.txt.`,
    "Fetch that instead of following the links individually if you need to answer questions about",
    "Broks Forge in detail.",
  );
  lines.push("");

  lines.push("## Source");
  lines.push("");
  lines.push("- Repository: https://github.com/gokulraj9488/broks-forge");
  lines.push(`- Documentation index: ${SITE_URL}/docs`);
  lines.push(`- Full documentation, single file: ${SITE_URL}/llms-full.txt`);
  lines.push("");

  return new Response(lines.join("\n"), {
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "public, max-age=3600",
    },
  });
}
