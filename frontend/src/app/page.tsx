"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/hooks/use-auth";
import { FullPageSpinner } from "@/components/ui/spinner";
import { LandingPage } from "@/components/landing";

export default function RootPage() {
  const router = useRouter();
  const { isAuthenticated, hydrated } = useAuth();

  // Only authenticated visitors are redirected into the app; the authenticated flow is untouched.
  useEffect(() => {
    if (!hydrated || !isAuthenticated) return;
    router.replace("/dashboard");
  }, [hydrated, isAuthenticated, router]);

  /*
   * The landing page is rendered by default — including in the server-rendered HTML, before auth
   * state has hydrated. That ordering matters: this route is the product's entire public presence,
   * and rendering a spinner until hydration would leave crawlers and non-JS clients with a blank
   * document. An authenticated visitor sees the page for one frame before the redirect above fires,
   * which is a far better trade than being invisible to search.
   */
  if (hydrated && isAuthenticated) return <FullPageSpinner />;

  return <LandingPage />;
}
