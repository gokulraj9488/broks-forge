/**
 * lucide-react has no brand marks (Spring Boot, PostgreSQL, Redis, Groq...), and pulling in a
 * logo-icon package for six wordmarks isn't worth the dependency. Monochrome uppercase wordmarks
 * read as an intentional, technical choice (same move Vercel/Linear "built with" strips use) and
 * cost nothing extra.
 */
const TECHNOLOGIES = ["Spring Boot", "PostgreSQL", "Redis", "Groq", "Docker", "React"];

export function TechMarquee() {
  const items = [...TECHNOLOGIES, ...TECHNOLOGIES];

  return (
    <section className="border-b border-border/60 bg-secondary/40 py-10">
      <div className="container">
        <p className="mb-6 text-center text-xs font-medium uppercase tracking-wider text-muted-foreground">
          Built on a real production stack
        </p>
      </div>
      <div
        className="relative overflow-hidden [mask-image:linear-gradient(to_right,transparent,black_10%,black_90%,transparent)]"
        role="list"
        aria-label="Technologies used"
      >
        <div className="flex w-max animate-marquee gap-16 motion-reduce:animate-none">
          {items.map((name, i) => (
            <span
              key={`${name}-${i}`}
              role="listitem"
              className="shrink-0 text-xl font-semibold tracking-tight text-muted-foreground/50 grayscale transition-colors hover:text-foreground"
            >
              {name}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}
