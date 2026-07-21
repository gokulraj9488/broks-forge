"use client";

import Link from "next/link";
import { useState } from "react";
import { Github, Menu, X } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const LINKS = [
  { href: "#features", label: "Features" },
  { href: "#workflow", label: "Workflow" },
  { href: "#screenshots", label: "Screenshots" },
  { href: "https://github.com/your-org/broks-forge", label: "GitHub", external: true },
  { href: "/docs", label: "Docs" },
];

export function LandingNav() {
  const [open, setOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 border-b border-border/60 bg-background/80 backdrop-blur-md">
      <div className="container flex h-16 items-center justify-between">
        <Link href="/" className="flex items-center">
          <Logo />
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
          {LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              target={link.external ? "_blank" : undefined}
              rel={link.external ? "noopener noreferrer" : undefined}
              className="flex items-center gap-1.5 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent/60 hover:text-foreground"
            >
              {link.label === "GitHub" && <Github className="h-3.5 w-3.5" />}
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="hidden md:block">
          <Button asChild size="sm">
            <Link href="/login">Get Started</Link>
          </Button>
        </div>

        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          className="inline-flex h-9 w-9 items-center justify-center rounded-md text-foreground md:hidden"
          aria-label={open ? "Close menu" : "Open menu"}
          aria-expanded={open}
        >
          {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </div>

      <div
        className={cn(
          "overflow-hidden border-t border-border/60 transition-[max-height] duration-200 md:hidden",
          open ? "max-h-80" : "max-h-0 border-t-0",
        )}
      >
        <nav className="container flex flex-col gap-1 py-3">
          {LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              target={link.external ? "_blank" : undefined}
              rel={link.external ? "noopener noreferrer" : undefined}
              onClick={() => setOpen(false)}
              className="rounded-md px-3 py-2 text-sm font-medium text-muted-foreground hover:bg-accent/60 hover:text-foreground"
            >
              {link.label}
            </Link>
          ))}
          <Button asChild size="sm" className="mt-2">
            <Link href="/login">Get Started</Link>
          </Button>
        </nav>
      </div>
    </header>
  );
}
