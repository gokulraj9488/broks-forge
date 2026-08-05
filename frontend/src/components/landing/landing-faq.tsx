"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Reveal } from "./reveal";

/**
 * The questions a first-time visitor actually has, answered plainly.
 *
 * Rendered as real markup (not an accordion) so the text is in the page for crawlers and LLMs, and
 * emitted as FAQPage structured data for the same reason. The answers deliberately include the
 * unflattering ones — maturity, missing features — because a FAQ that only sells is not read as a
 * FAQ.
 */
const FAQS = [
  {
    q: "Is this an observability tool?",
    a: "No. Observability answers what happened. Broks Forge answers why the system is the way it is, what evidence supports it, and what to do next. They model different objects, and running both is sensible.",
  },
  {
    q: "Is Brok a chatbot? Does it use an LLM?",
    a: "Brok is not a chatbot and contains no language model. It resolves your question to one of 25 engineering intents by deterministic phrase scoring, then composes the answer from real database rows. Ask something the record cannot answer and it refuses.",
  },
  {
    q: "Do I have to use a specific framework?",
    a: "No. Agents are registered by HTTP endpoint. LangChain, LlamaIndex, a custom FastAPI service, a serverless function — if it is callable over REST, it works. There is no SDK to adopt.",
  },
  {
    q: "Is it in my production request path?",
    a: "No. Broks Forge is not a proxy or a gateway. It calls your agent endpoint during an evaluation and never otherwise, so it adds no latency to your traffic.",
  },
  {
    q: "Is it open source? What does it cost?",
    a: "Open source under Apache 2.0 and self-hostable with Docker Compose. No licence fee, no per-seat cost. You pay for the infrastructure you run it on and any model calls your evaluations make.",
  },
  {
    q: "How mature is it?",
    a: "Early. It is a complete, tested system — 499 backend tests running against real PostgreSQL — but it is a young project without the production track record or support organization of the commercial tools it is compared with.",
  },
  {
    q: "What is the minimum useful setup?",
    a: "An agent, a dataset, a prompt and one evaluation. That already produces observations, evidence, a graph, an AI Git timeline and answerable questions. The habit that matters most is writing one honest sentence on every version.",
  },
  {
    q: "Why does it say “unknown” instead of “healthy”?",
    a: "Because nothing has measured that artifact. Absence of failure is not evidence of health, and the platform refuses to imply otherwise. Run an evaluation and it becomes a real verdict.",
  },
];

export function LandingFaq() {
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: FAQS.map((f) => ({
      "@type": "Question",
      name: f.q,
      acceptedAnswer: { "@type": "Answer", text: f.a },
    })),
  };

  return (
    <section id="faq" className="border-b border-border/60 py-20 sm:py-24">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <div className="container">
        <Reveal>
          <p className="text-sm font-medium uppercase tracking-wide text-primary">FAQ</p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            Frequently asked questions
          </h2>
        </Reveal>

        <dl className="mt-12 grid grid-cols-1 gap-x-10 gap-y-8 lg:grid-cols-2">
          {FAQS.map((f, i) => (
            <Reveal key={f.q} delay={i * 0.03}>
              <dt className="text-base font-semibold text-foreground">{f.q}</dt>
              <dd className="mt-2 text-sm leading-relaxed text-muted-foreground">{f.a}</dd>
            </Reveal>
          ))}
        </dl>

        <Reveal delay={0.1}>
          <Link
            href="/docs/faq"
            className="mt-10 inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
          >
            Read the full FAQ
            <ArrowRight className="h-4 w-4" />
          </Link>
        </Reveal>
      </div>
    </section>
  );
}
