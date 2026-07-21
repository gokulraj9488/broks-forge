import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Reveal } from "./reveal";

export function FinalCta() {
  return (
    <section className="py-24">
      <div className="container">
        <Reveal className="mx-auto flex max-w-2xl flex-col items-center rounded-2xl border border-border/60 bg-card px-8 py-16 text-center shadow-sm">
          <h2 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            Ready to engineer AI like software?
          </h2>
          <Button asChild size="lg" className="mt-8 transition-transform active:scale-[0.97]">
            <Link href="/login">
              Enter Platform
              <ArrowUpRight className="h-4 w-4" />
            </Link>
          </Button>
        </Reveal>
      </div>
    </section>
  );
}
