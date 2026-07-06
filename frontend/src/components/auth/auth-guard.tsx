"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/hooks/use-auth";
import { useIdleTimeout } from "@/lib/hooks/use-idle-timeout";
import { IdleWarningDialog } from "@/components/auth/idle-warning-dialog";
import { FullPageSpinner } from "@/components/ui/spinner";

/**
 * Client-side route protection. Tokens live in localStorage (not cookies), so
 * protection happens after hydration rather than in Next middleware.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { isAuthenticated, hydrated } = useAuth();

  // Auto sign-out after a configurable period of inactivity, with a 60s warning;
  // only armed while an authenticated view is mounted.
  const { warningActive, secondsLeft, stayActive, logoutNow } = useIdleTimeout(
    hydrated && isAuthenticated,
  );

  useEffect(() => {
    if (hydrated && !isAuthenticated) {
      router.replace("/login");
    }
  }, [hydrated, isAuthenticated, router]);

  if (!hydrated || !isAuthenticated) {
    return <FullPageSpinner />;
  }

  return (
    <>
      {children}
      <IdleWarningDialog
        open={warningActive}
        secondsLeft={secondsLeft}
        onStay={stayActive}
        onLogout={logoutNow}
      />
    </>
  );
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
