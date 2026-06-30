"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/hooks/use-auth";
import { FullPageSpinner } from "@/components/ui/spinner";

/**
 * Client-side route protection. Tokens live in localStorage (not cookies), so
 * protection happens after hydration rather than in Next middleware.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { isAuthenticated, hydrated } = useAuth();

  useEffect(() => {
    if (hydrated && !isAuthenticated) {
      router.replace("/login");
    }
  }, [hydrated, isAuthenticated, router]);

  if (!hydrated || !isAuthenticated) {
    return <FullPageSpinner />;
  }

  return <>{children}</>;
}

/** Inverse of AuthGuard: redirects authenticated users away from auth pages. */
export function GuestGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { isAuthenticated, hydrated } = useAuth();

  useEffect(() => {
    if (hydrated && isAuthenticated) {
      router.replace("/dashboard");
    }
  }, [hydrated, isAuthenticated, router]);

  if (!hydrated) {
    return <FullPageSpinner />;
  }

  return <>{children}</>;
}
