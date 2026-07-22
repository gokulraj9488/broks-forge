import fs from "node:fs";
import path from "node:path";

/**
 * Curated engineering docs exposed at /docs — copied at
 * `frontend/content/docs/` (see that folder's source: the repo's root
 * `docs/` directory) so they're inside the Docker build context and
 * available at `next build` time. Order here is the sidebar/index order.
 */
const DOC_FILES = [
  { slug: "architecture", file: "MASTER_ARCHITECTURE.md", title: "Architecture" },
  { slug: "engineering-handbook", file: "ENGINEERING_HANDBOOK.md", title: "Engineering Handbook" },
  { slug: "developer-guide", file: "DEVELOPER_GUIDE.md", title: "Developer Guide" },
  { slug: "project-rules", file: "PROJECT_RULES.md", title: "Project Rules" },
  { slug: "coding-standards", file: "CODING_STANDARDS.md", title: "Coding Standards" },
  { slug: "api-guidelines", file: "API_GUIDELINES.md", title: "API Guidelines" },
  { slug: "security", file: "SECURITY_GUIDE.md", title: "Security" },
  { slug: "error-handling", file: "ERROR_HANDLING_GUIDE.md", title: "Error Handling" },
  { slug: "testing-strategy", file: "TESTING_STRATEGY.md", title: "Testing Strategy" },
  { slug: "performance", file: "PERFORMANCE_GUIDE.md", title: "Performance" },
  { slug: "deployment", file: "DEPLOYMENT.md", title: "Deployment" },
  { slug: "contributing", file: "CONTRIBUTING.md", title: "Contributing" },
  { slug: "roadmap", file: "ROADMAP.md", title: "Roadmap" },
] as const;

const DOCS_DIR = path.join(process.cwd(), "content", "docs");

export interface DocMeta {
  slug: string;
  title: string;
  description: string;
}

function firstParagraph(body: string): string {
  const withoutTitle = body.replace(/^#[^\n]*\n/, "");
  const match = withoutTitle
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .find((p) => p && !p.startsWith("#") && !p.startsWith(">") && !p.startsWith("```"));
  return (match ?? "").replace(/[*_`]/g, "").replace(/\s+/g, " ").slice(0, 160);
}

export function getAllDocs(): DocMeta[] {
  return DOC_FILES.map(({ slug, file, title }) => {
    const raw = fs.readFileSync(path.join(DOCS_DIR, file), "utf-8");
    return { slug, title, description: firstParagraph(raw) };
  });
}

export function getDocSlugs(): string[] {
  return DOC_FILES.map((d) => d.slug);
}

export function getDocBySlug(slug: string): { title: string; content: string } | null {
  const entry = DOC_FILES.find((d) => d.slug === slug);
  if (!entry) return null;
  const content = fs.readFileSync(path.join(DOCS_DIR, entry.file), "utf-8");
  return { title: entry.title, content };
}
