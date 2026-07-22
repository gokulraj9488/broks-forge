import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { getAllDocs } from "@/lib/docs";

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  const docs = getAllDocs();

  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header className="sticky top-0 z-50 border-b border-border/60 bg-background/80 backdrop-blur-md">
        <div className="container flex h-16 items-center justify-between">
          <Link href="/" className="flex items-center">
            <Logo />
          </Link>
          <Link
            href="/"
            className="flex items-center gap-1.5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            Back to home
          </Link>
        </div>
      </header>

      <div className="container flex-1 gap-10 py-10 lg:grid lg:grid-cols-[220px_1fr]">
        <aside className="mb-8 lg:mb-0">
          <p className="mb-3 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Documentation
          </p>
          <nav className="flex flex-row flex-wrap gap-1 lg:flex-col">
            {docs.map((doc) => (
              <Link
                key={doc.slug}
                href={`/docs/${doc.slug}`}
                className="rounded-md px-3 py-1.5 text-sm text-muted-foreground transition-colors hover:bg-accent/60 hover:text-foreground lg:px-2.5"
              >
                {doc.title}
              </Link>
            ))}
          </nav>
        </aside>

        <main className="min-w-0 pb-16">{children}</main>
      </div>
    </div>
  );
}
