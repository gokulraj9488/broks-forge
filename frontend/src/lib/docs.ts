import fs from "node:fs";
import path from "node:path";

/**
 * The public documentation registry.
 *
 * Two kinds of document live under `frontend/content/docs/`, and the distinction matters:
 *
 *  1. **Public documentation** (lowercase filenames) — written for the website. These explain what Broks
 *     Forge is, the category it belongs to, each capability, how it compares to adjacent tools, and how to
 *     build on it. Their canonical home is this folder; they are website content, not repo engineering docs.
 *  2. **The engineering handbook** (UPPERCASE filenames) — copies of the repo's own `docs/` directory,
 *     published unedited so the rules the project is actually built under are public too.
 *
 * Order here is the order of the sidebar and the index, and it is deliberately a reading order: a visitor
 * who starts at the top and works down arrives at a complete understanding without needing the source.
 */

export interface DocEntry {
  slug: string;
  file: string;
  title: string;
  /** One line, used for the index card, the sidebar tooltip and the page's meta description. */
  summary: string;
}

export interface DocSection {
  id: string;
  title: string;
  blurb: string;
  docs: DocEntry[];
}

export const DOC_SECTIONS: DocSection[] = [
  {
    id: "introduction",
    title: "Introduction",
    blurb: "What Broks Forge is, the category it belongs to, and how to run it.",
    docs: [
      {
        slug: "what-is-broks-forge",
        file: "what-is-broks-forge.md",
        title: "What is Broks Forge?",
        summary:
          "The canonical answer: an AI Engineering Operating System that records the engineering act behind an AI system and reasons over it.",
      },
      {
        slug: "ai-engineering-operating-system",
        file: "ai-engineering-operating-system.md",
        title: "The AI Engineering Operating System",
        summary:
          "The category explained: the problems AI teams face, why observability is not enough, and what an operating system for AI engineering has to model.",
      },
      {
        slug: "getting-started",
        file: "getting-started.md",
        title: "Getting Started",
        summary:
          "Run the stack, register your first agent, dataset and prompt, evaluate them, and read the engineering record that results.",
      },
      {
        slug: "the-five-layers",
        file: "the-five-layers.md",
        title: "The Five Layers",
        summary:
          "Forge Kernel, Registry, AI Git, Forge Graph and Engineering Applications — what each layer owns and why the order matters.",
      },
    ],
  },
  {
    id: "concepts",
    title: "Core Concepts",
    blurb: "The object model and the ideas the whole platform is built on.",
    docs: [
      {
        slug: "core-concepts",
        file: "core-concepts.md",
        title: "Core Concepts",
        summary:
          "Artifacts, revisions, evaluations, runs, and the derived reasoning objects — Observation, Claim, Decision, Evidence and Knowledge.",
      },
      {
        slug: "engineering-intelligence",
        file: "engineering-intelligence.md",
        title: "Engineering Intelligence",
        summary:
          "How Broks Forge derives observations, claims, decisions, evidence and knowledge from real engineering work, without anyone writing them down.",
      },
      {
        slug: "engineering-memory",
        file: "engineering-memory.md",
        title: "Engineering Memory",
        summary:
          "Why a system that remembers why things are the way they are behaves differently from one that only remembers what happened.",
      },
      {
        slug: "knowledge",
        file: "knowledge.md",
        title: "Knowledge",
        summary:
          "Durable engineering facts that emerge from decisions and evidence — never authored, never fabricated, always traceable.",
      },
      {
        slug: "why-observability-is-not-enough",
        file: "why-observability-is-not-enough.md",
        title: "Why Observability Is Not Enough",
        summary:
          "Traces answer what happened. Engineering questions are about why, what changed, what it means and what to do — a different data model.",
      },
      {
        slug: "deterministic-reasoning",
        file: "deterministic-reasoning.md",
        title: "Deterministic Engineering Reasoning",
        summary:
          "Why the reasoning layer is a deterministic engine over real records rather than a language model, and what that guarantees.",
      },
    ],
  },
  {
    id: "capabilities",
    title: "Capabilities",
    blurb: "Each surface, what question it answers, and how it works.",
    docs: [
      {
        slug: "registry",
        file: "registry.md",
        title: "Registry",
        summary:
          "One catalog of every engineering artifact and every piece of knowledge derived from it — discovery in one place, not per module.",
      },
      {
        slug: "ai-git",
        file: "ai-git.md",
        title: "AI Git",
        summary:
          "Version control for engineering reasoning: revisions, promotions, rollbacks and the rationale behind each change.",
      },
      {
        slug: "forge-graph",
        file: "forge-graph.md",
        title: "Forge Graph",
        summary:
          "Your AI organization as a connected system — artifacts, their real relationships, and reasoning layered on top of them.",
      },
      {
        slug: "execution-graph",
        file: "execution-graph.md",
        title: "Execution Graph & Failure Graph",
        summary:
          "The runtime path of a single evaluation run, reconstructed from its own telemetry — and the same graph narrowed to where the chain broke.",
      },
      {
        slug: "evolution",
        file: "evolution.md",
        title: "Evolution",
        summary:
          "Lineage and blast radius: what an artifact depends on, what depends on it, and what a change here would affect.",
      },
      {
        slug: "brok",
        file: "brok.md",
        title: "Brok — the Engineering Partner",
        summary:
          "Ask engineering questions in plain English and get answers read from your own record, each one declaring how it is known.",
      },
      {
        slug: "root-cause-explorer",
        file: "root-cause-explorer.md",
        title: "Root Cause Explorer",
        summary:
          "The Engineering Investigation Workspace: a chronology, a cause at four depths, and every chain of evidence behind a failure.",
      },
      {
        slug: "evaluations",
        file: "evaluations.md",
        title: "Evaluations & Metrics",
        summary:
          "Reproducible measurement: pinned configurations, real runs, the metric catalog and the failure classifier behind them.",
      },
    ],
  },
  {
    id: "workflow",
    title: "Working With Broks Forge",
    blurb: "The engineering loop, worked examples and the practices that make it pay off.",
    docs: [
      {
        slug: "engineering-workflow",
        file: "engineering-workflow.md",
        title: "The Engineering Workflow",
        summary:
          "Problem → Execution → Evidence → Knowledge → Decision → Revision → Promotion → Deployment → Learning, end to end.",
      },
      {
        slug: "examples",
        file: "examples.md",
        title: "Examples",
        summary:
          "Four worked scenarios: a failing deployment, a prompt regression, an unexplained cost rise, and a promotion nobody can defend.",
      },
      {
        slug: "best-practices",
        file: "best-practices.md",
        title: "Best Practices",
        summary:
          "How to get a trustworthy engineering record: what to version, what to evaluate, and what to record a reason for.",
      },
    ],
  },
  {
    id: "comparisons",
    title: "Comparisons",
    blurb: "How Broks Forge differs in scope and philosophy from adjacent tools. Factual, not competitive.",
    docs: [
      {
        slug: "comparisons",
        file: "comparisons.md",
        title: "Comparisons Overview",
        summary:
          "Where Broks Forge sits relative to tracing, evaluation, gateway and experiment-tracking tools — and where it overlaps.",
      },
      {
        slug: "vs-langfuse",
        file: "vs-langfuse.md",
        title: "Broks Forge vs LangFuse",
        summary: "Tracing and evaluation for LLM apps, compared with an engineering record and reasoning layer.",
      },
      {
        slug: "vs-langsmith",
        file: "vs-langsmith.md",
        title: "Broks Forge vs LangSmith",
        summary: "The LangChain-native observability and evaluation suite, compared with a framework-agnostic engineering OS.",
      },
      {
        slug: "vs-promptfoo",
        file: "vs-promptfoo.md",
        title: "Broks Forge vs Promptfoo",
        summary: "A developer-first prompt testing CLI, compared with a persistent, multi-user engineering record.",
      },
      {
        slug: "vs-helicone",
        file: "vs-helicone.md",
        title: "Broks Forge vs Helicone",
        summary: "An LLM gateway and observability proxy, compared with a platform that reasons about engineering decisions.",
      },
      {
        slug: "vs-weights-and-biases",
        file: "vs-weights-and-biases.md",
        title: "Broks Forge vs Weights & Biases",
        summary: "Experiment tracking built for model training, compared with engineering intelligence for AI systems in production.",
      },
    ],
  },
  {
    id: "developer",
    title: "Developer Documentation",
    blurb: "Architecture, the data model, the REST API, extension points and how to build the project.",
    docs: [
      {
        slug: "architecture",
        file: "architecture-overview.md",
        title: "Architecture Overview",
        summary:
          "The system in one page: Spring Boot modules, the Next.js app, the derivation pipeline and where each layer lives.",
      },
      {
        slug: "data-model",
        file: "data-model.md",
        title: "Data Model",
        summary:
          "What is stored versus what is derived — the persisted tables, the composite ids, and the reasoning objects computed on read.",
      },
      {
        slug: "rest-api",
        file: "rest-api.md",
        title: "REST API",
        summary:
          "Conventions, authentication, tenancy, pagination and errors — plus the endpoints for every capability.",
      },
      {
        slug: "module-structure",
        file: "module-structure.md",
        title: "Module Structure",
        summary: "How the backend and frontend are organised, and the rules a new module has to follow.",
      },
      {
        slug: "extension-points",
        file: "extension-points.md",
        title: "Extension Points",
        summary:
          "Adding a provider, a metric, a Brok intent, a brief or an investigation cause — the seams designed to be extended.",
      },
      {
        slug: "developer-setup",
        file: "developer-setup.md",
        title: "Developer Setup & Build",
        summary: "Prerequisites, running the stack, the test suites, and the build commands that gate a change.",
      },
      {
        slug: "engineering-principles",
        file: "engineering-principles.md",
        title: "Engineering Principles",
        summary:
          "The rules the codebase is held to: one product, evolve don't duplicate, derive don't store, never fabricate.",
      },
    ],
  },
  {
    id: "reference",
    title: "Reference",
    blurb: "Terminology and the questions people ask first.",
    docs: [
      {
        slug: "faq",
        file: "faq.md",
        title: "FAQ",
        summary: "Straight answers about scope, cost, self-hosting, model support, data handling and maturity.",
      },
      {
        slug: "glossary",
        file: "glossary.md",
        title: "Glossary",
        summary: "Every term Broks Forge uses, defined once and used consistently across the product and the docs.",
      },
    ],
  },
  {
    id: "handbook",
    title: "Engineering Handbook",
    blurb:
      "The repository's own engineering documents, published unedited — the rules this project is actually built under.",
    docs: [
      {
        slug: "master-architecture",
        file: "MASTER_ARCHITECTURE.md",
        title: "Master Architecture",
        summary: "The full internal architecture reference.",
      },
      {
        slug: "engineering-handbook",
        file: "ENGINEERING_HANDBOOK.md",
        title: "Engineering Handbook",
        summary: "How the team works: standards, review and delivery.",
      },
      {
        slug: "developer-guide",
        file: "DEVELOPER_GUIDE.md",
        title: "Developer Guide",
        summary: "Day-to-day development reference.",
      },
      {
        slug: "project-rules",
        file: "PROJECT_RULES.md",
        title: "Project Rules",
        summary: "Non-negotiable rules for changes to this codebase.",
      },
      {
        slug: "coding-standards",
        file: "CODING_STANDARDS.md",
        title: "Coding Standards",
        summary: "Language-level conventions for Java and TypeScript.",
      },
      {
        slug: "api-guidelines",
        file: "API_GUIDELINES.md",
        title: "API Guidelines",
        summary: "REST conventions every endpoint follows.",
      },
      {
        slug: "security",
        file: "SECURITY_GUIDE.md",
        title: "Security",
        summary: "Authentication, authorization, tenancy and credential handling.",
      },
      {
        slug: "error-handling",
        file: "ERROR_HANDLING_GUIDE.md",
        title: "Error Handling",
        summary: "How failures are represented and surfaced.",
      },
      {
        slug: "testing-strategy",
        file: "TESTING_STRATEGY.md",
        title: "Testing Strategy",
        summary: "What is tested, at which level, and why.",
      },
      {
        slug: "performance",
        file: "PERFORMANCE_GUIDE.md",
        title: "Performance",
        summary: "Performance budgets and the practices that hold them.",
      },
      {
        slug: "deployment",
        file: "DEPLOYMENT.md",
        title: "Deployment",
        summary: "Running Broks Forge in a real environment.",
      },
      {
        slug: "contributing",
        file: "CONTRIBUTING.md",
        title: "Contributing",
        summary: "How to propose and land a change.",
      },
      {
        slug: "roadmap",
        file: "ROADMAP.md",
        title: "Roadmap",
        summary: "What exists today and what is planned.",
      },
    ],
  },
];

/** Flattened, in reading order. */
export const DOC_FILES: DocEntry[] = DOC_SECTIONS.flatMap((s) => s.docs);

const DOCS_DIR = path.join(process.cwd(), "content", "docs");

export interface DocMeta {
  slug: string;
  title: string;
  description: string;
}

export function getDocSections(): DocSection[] {
  return DOC_SECTIONS;
}

export function getAllDocs(): DocMeta[] {
  return DOC_FILES.map(({ slug, title, summary }) => ({ slug, title, description: summary }));
}

export function getDocSlugs(): string[] {
  return DOC_FILES.map((d) => d.slug);
}

export function getDocEntry(slug: string): DocEntry | null {
  return DOC_FILES.find((d) => d.slug === slug) ?? null;
}

/** The section a document belongs to — used for breadcrumbs and prev/next. */
export function getDocSection(slug: string): DocSection | null {
  return DOC_SECTIONS.find((s) => s.docs.some((d) => d.slug === slug)) ?? null;
}

/** Sequential navigation across the whole reading order, so a visitor can simply keep going. */
export function getDocNeighbours(slug: string): { previous: DocEntry | null; next: DocEntry | null } {
  const index = DOC_FILES.findIndex((d) => d.slug === slug);
  if (index < 0) {
    return { previous: null, next: null };
  }
  return {
    previous: index > 0 ? DOC_FILES[index - 1] : null,
    next: index < DOC_FILES.length - 1 ? DOC_FILES[index + 1] : null,
  };
}

export function getDocBySlug(slug: string): { title: string; summary: string; content: string } | null {
  const entry = getDocEntry(slug);
  if (!entry) {
    return null;
  }
  const filePath = path.join(DOCS_DIR, entry.file);
  if (!fs.existsSync(filePath)) {
    return null;
  }
  return { title: entry.title, summary: entry.summary, content: fs.readFileSync(filePath, "utf-8") };
}
