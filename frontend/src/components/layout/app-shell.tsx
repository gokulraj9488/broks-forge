"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Menu, Sparkles, X } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { SidebarNav } from "@/components/layout/sidebar-nav";
import { UserMenu } from "@/components/layout/user-menu";
import { Button } from "@/components/ui/button";
import { ContactDeveloperLink } from "@/components/common/contact-developer";
import { cn } from "@/lib/utils";

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <div className="flex h-full flex-col">
      {/* Logo: pinned, never scrolls. */}
      <div className="flex h-16 shrink-0 items-center border-b border-border px-5">
        <Link href="/dashboard" onClick={onNavigate}>
          <Logo />
        </Link>
      </div>

      {/* Only the nav list scrolls — logo and footer stay pinned, and every group/item
          stays reachable no matter how tall the list grows. The gradient overlays give a
          subtle "more content below/above" fade, similar to GitHub/Google Cloud Console. */}
      <div className="relative min-h-0 flex-1">
        <div className="h-full overflow-y-auto">
          <SidebarNav onNavigate={onNavigate} />
        </div>
        <div className="pointer-events-none absolute inset-x-0 top-0 h-4 bg-gradient-to-b from-card to-transparent" />
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-4 bg-gradient-to-t from-card to-transparent" />
      </div>

      {/* Footer: pinned, never scrolls. */}
      <div className="shrink-0 space-y-1.5 border-t border-border p-4">
        <p className="text-[11px] text-muted-foreground">
          Need help? <ContactDeveloperLink className="text-[11px]" />
        </p>
        <p className="text-[11px] leading-relaxed text-muted-foreground">
          Brok&apos;s Forge · v1.0.0
          <br />
          The AI Engineering Operating System.
        </p>
      </div>
    </div>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const pathname = usePathname();
  const router = useRouter();

  // Brok is one keystroke from anywhere. Ctrl+. (Cmd+. on Mac) travels to the Engineering Partner —
  // a navigation, not an overlay: constitutionally Brok is a workspace you go to, not a widget.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "." && (e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey) {
        e.preventDefault();
        router.push("/brok");
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [router]);

  return (
    <div className="min-h-screen bg-background">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 border-r border-border bg-card lg:block">
        <SidebarContent />
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="absolute inset-0 bg-black/70 backdrop-blur-sm"
            onClick={() => setMobileOpen(false)}
          />
          <aside className="absolute inset-y-0 left-0 w-64 border-r border-border bg-card shadow-xl">
            <button
              className="absolute right-3 top-4 text-muted-foreground hover:text-foreground"
              onClick={() => setMobileOpen(false)}
              aria-label="Close menu"
            >
              <X className="h-5 w-5" />
            </button>
            <SidebarContent onNavigate={() => setMobileOpen(false)} />
          </aside>
        </div>
      )}

      {/* Main column */}
      <div className={cn("flex min-h-screen flex-col lg:pl-64")}>
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between gap-3 border-b border-border bg-background/80 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/60 sm:px-6">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="icon"
              className="lg:hidden"
              onClick={() => setMobileOpen(true)}
              aria-label="Open menu"
            >
              <Menu className="h-5 w-5" />
            </Button>
            <div className="lg:hidden">
              <Logo showWordmark={false} />
            </div>
          </div>
          <div className="flex items-center gap-3">
            {/* The one entry point visible on every page — how a first-time user discovers Brok. */}
            {pathname !== "/brok" && (
              <Link
                href="/brok"
                className="inline-flex items-center gap-1.5 rounded-lg border border-border px-2.5 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:border-primary/40 hover:text-foreground"
              >
                <Sparkles className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">Ask Brok</span>
                <kbd className="hidden rounded border border-border bg-muted px-1 py-0.5 text-[9px] font-normal text-muted-foreground sm:inline">
                  Ctrl&nbsp;.
                </kbd>
              </Link>
            )}
            <UserMenu />
          </div>
        </header>

        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          {/* Keyed by route so each navigation re-triggers the fade-in transition. */}
          <div key={pathname} className="mx-auto w-full max-w-6xl animate-fade-in">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
