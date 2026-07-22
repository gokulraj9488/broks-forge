"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/hooks/use-auth";
import { FullPageSpinner } from "@/components/ui/spinner";
import { LandingPage } from "@/components/landing";

export default function RootPage() {
  const router = useRouter();
  const { isAuthenticated, hydrated } = useAuth();

  // Only authenticated visitors are redirected straight into the app — the
  // authenticated flow (dashboard, everything under it) is otherwise untouched.
  // Unauthenticated visitors render the marketing landing page below instead
  // of being bounced straight to /login.
  useEffect(() => {
    if (!hydrated || !isAuthenticated) return;
    router.replace("/dashboard");
  }, [hydrated, isAuthenticated, router]);

  if (!hydrated || isAuthenticated) return <FullPageSpinner />;

  return <LandingPage />;
}
