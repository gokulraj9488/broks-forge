import Link from "next/link";
import { ArrowUpRight, BookOpen, Github, Terminal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Reveal } from "./reveal";

/**
 * The closing call to action offers three real doors rather than one funnel: run it, read it, or
 * read the source. For an open-source engineering tool the second and third are as legitimate as
 * the first, and pretending otherwise costs credibility with exactly the audience this is for.
 */
export function FinalCta() {
  return (
    <section className="py-20 sm:py-24">
      <div className="container">
        <Reveal className="mx-auto max-w-3xl rounded-2xl border border-border/60 bg-card px-8 py-14 text-center shadow-sm">
          <h2 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            Give your AI system an engineering record.
          </h2>
          <p className="mx-auto mt-4 max-w-xl text-base leading-relaxed text-muted-foreground">
            Self-hosted, open source, and running in about fifteen minutes — most of it waiting on
            the first Docker build.
          </p>

          <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
            <Button asChild size="lg" className="transition-transform active:scale-[0.97]">
              <Link href="/register">
                Get Started
                <ArrowUpRight className="h-4 w-4" />
              </Link>
            </Button>
            <Button asChild size="lg" variant="outline" className="transition-transform active:scale-[0.97]">
              <Link href="/docs/getting-started">
                <Terminal className="h-4 w-4" />
                Run it locally
              </Link>
            </Button>
            <Button asChild size="lg" variant="ghost" className="transition-transform active:scale-[0.97]">
              <Link
                href="https://github.com/gokulraj9488/broks-forge"
                target="_blank"
                rel="noopener noreferrer"
              >
                <Github className="h-4 w-4" />
                Read the source
              </Link>
            </Button>
          </div>

          <p className="mt-8 flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
            <BookOpen className="h-3.5 w-3.5" />
            New to the category?
            <Link
              href="/docs/ai-engineering-operating-system"
              className="font-medium text-primary hover:underline"
            >
              What is an AI Engineering Operating System?
            </Link>
          </p>
        </Reveal>
      </div>
    </section>
  );
}
