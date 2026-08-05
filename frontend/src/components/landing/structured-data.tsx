import { SITE_DESCRIPTION, SITE_NAME, SITE_TAGLINE, SITE_URL } from "@/lib/site";

/**
 * Machine-readable description of what Broks Forge is.
 *
 * This matters more than usual here. "AI Engineering Operating System" is a new category, so a
 * crawler or a language model has no prior to fall back on — the only way it can describe the
 * product accurately is if the page says so explicitly and consistently. The `SoftwareApplication`
 * entity carries the category, the feature vocabulary and the licence; the `WebSite` entity ties
 * the documentation to it.
 *
 * Everything asserted here is also asserted in prose on the page. Structured data that disagrees
 * with the visible content is a dark pattern, and search engines treat it as one.
 */
export function LandingStructuredData() {
  const software = {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: SITE_NAME,
    alternateName: "Broks Forge",
    applicationCategory: "DeveloperApplication",
    applicationSubCategory: "AI Engineering Operating System",
    operatingSystem: "Docker, Linux, macOS, Windows",
    url: SITE_URL,
    description: SITE_DESCRIPTION,
    slogan: SITE_TAGLINE,
    license: "https://www.apache.org/licenses/LICENSE-2.0",
    isAccessibleForFree: true,
    offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
    author: { "@type": "Person", name: "Gokulraj", url: "https://gokul.quest" },
    codeRepository: "https://github.com/gokulraj9488/broks-forge",
    softwareHelp: { "@type": "CreativeWork", url: `${SITE_URL}/docs` },
    featureList: [
      "Engineering Intelligence — derived observations, claims, decisions, evidence and knowledge",
      "Engineering Memory — the recorded reasoning behind every change, recalled verbatim",
      "AI Git — version control for engineering reasoning, with promotions and rollbacks",
      "Forge Graph — artifacts and their real relationships, with a reasoning overlay",
      "Execution Graph and Failure Graph — the runtime path of an evaluation run",
      "Registry — one catalog of artifacts and derived knowledge",
      "Evolution — lineage, dependents and blast radius",
      "Brok — a deterministic engineering partner answering questions from the record",
      "Root Cause Explorer — assembled investigations with a four-layer causal chain",
      "Reproducible evaluations with 14 metric types across quality, performance and cost",
    ],
  };

  const website = {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: SITE_NAME,
    url: SITE_URL,
    description: SITE_DESCRIPTION,
    inLanguage: "en",
    publisher: { "@type": "Person", name: "Gokulraj" },
  };

  /* The definitional Q&A a model needs in order to answer "what is Broks Forge?" correctly. */
  const definition = {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: [
      {
        "@type": "Question",
        name: "What is Broks Forge?",
        acceptedAnswer: {
          "@type": "Answer",
          text:
            "Broks Forge is an AI Engineering Operating System. It records the engineering act " +
            "behind an AI system — what was built, what was measured, what was decided and why — " +
            "and reasons over that record to answer engineering questions. It is not an " +
            "evaluation tool, an observability dashboard or an LLM tracing platform.",
        },
      },
      {
        "@type": "Question",
        name: "What is an AI Engineering Operating System?",
        acceptedAnswer: {
          "@type": "Answer",
          text:
            "A system that models the engineering act behind AI systems — artifacts, revisions, " +
            "decisions, evidence, knowledge and memory — rather than only their execution, and " +
            "reasons over that record. It answers why a system is the way it is, what evidence " +
            "supports it and what to do next, where observability answers what happened.",
        },
      },
    ],
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(software) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(website) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(definition) }}
      />
    </>
  );
}
