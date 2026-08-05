"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { DocSection } from "@/lib/docs";
import { cn } from "@/lib/utils";

/**
 * The documentation sidebar.
 *
 * Sections are rendered in reading order rather than alphabetically, because the order is itself
 * information: a visitor who starts at the top and works down arrives at a complete understanding of
 * the platform. The active page is marked so a reader always knows where they are in that sequence.
 */
export function DocsSidebar({ sections }: { sections: DocSection[] }) {
  const pathname = usePathname();

  return (
    <aside className="mb-10 lg:mb-0">
      <nav
        aria-label="Documentation"
        className="lg:sticky lg:top-24 lg:max-h-[calc(100dvh-8rem)] lg:overflow-y-auto lg:pr-2"
      >
        <Link
          href="/docs"
          className={cn(
            "mb-4 block rounded-md px-2.5 py-1.5 text-sm font-medium transition-colors",
            pathname === "/docs"
              ? "bg-accent text-foreground"
              : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
          )}
        >
          All documentation
        </Link>

        <div className="space-y-5">
          {sections.map((section) => (
            <div key={section.id}>
              <p className="mb-1.5 px-2.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground/70">
                {section.title}
              </p>
              <ul className="space-y-0.5">
                {section.docs.map((doc) => {
                  const href = `/docs/${doc.slug}`;
                  const active = pathname === href;
                  return (
                    <li key={doc.slug}>
                      <Link
                        href={href}
                        title={doc.summary}
                        aria-current={active ? "page" : undefined}
                        className={cn(
                          "block rounded-md px-2.5 py-1.5 text-sm leading-snug transition-colors",
                          active
                            ? "bg-accent font-medium text-foreground"
                            : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
                        )}
                      >
                        {doc.title}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </div>
      </nav>
    </aside>
  );
}
