import Link from "next/link";
import { ArrowLeft, Github } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { DocsSidebar } from "@/components/docs/docs-sidebar";
import { getDocSections } from "@/lib/docs";

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  const sections = getDocSections();

  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header className="sticky top-0 z-50 border-b border-border/60 bg-background/80 backdrop-blur-md">
        <div className="container flex h-16 items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Link href="/" className="flex items-center">
              <Logo />
            </Link>
            <span className="hidden text-sm text-muted-foreground sm:inline">Docs</span>
          </div>
          <div className="flex items-center gap-4">
            <Link
              href="https://github.com/gokulraj9488/broks-forge"
              target="_blank"
              rel="noopener noreferrer"
              className="hidden items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground sm:flex"
            >
              <Github className="h-3.5 w-3.5" />
              GitHub
            </Link>
            <Link
              href="/"
              className="flex items-center gap-1.5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              Home
            </Link>
          </div>
        </div>
      </header>

      <div className="container flex-1 gap-10 py-10 lg:grid lg:grid-cols-[16rem_1fr]">
        <DocsSidebar sections={sections} />
        <main className="min-w-0 pb-20">{children}</main>
      </div>
    </div>
  );
}
