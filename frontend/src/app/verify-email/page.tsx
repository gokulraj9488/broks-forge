"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { CheckCircle2, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { FullPageSpinner, Spinner } from "@/components/ui/spinner";
import { Logo } from "@/components/brand/logo";
import { useAuth } from "@/lib/hooks/use-auth";
import { authApi } from "@/lib/api/auth";
import { getApiErrorMessage } from "@/lib/api/client";

type Status = "pending" | "success" | "error";

function VerifyEmailInner() {
  const searchParams = useSearchParams();
  const { isAuthenticated } = useAuth();
  const token = searchParams.get("token") ?? "";
  const [status, setStatus] = useState<Status>("pending");
  const [message, setMessage] = useState("Verifying your email…");
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    if (!token) {
      setStatus("error");
      setMessage("This verification link is missing its token.");
      return;
    }
    authApi
      .verifyEmail(token)
      .then(() => {
        setStatus("success");
        setMessage("Your email address has been verified.");
      })
      .catch((error) => {
        setStatus("error");
        setMessage(getApiErrorMessage(error, "This link is invalid or has expired."));
      });
  }, [token]);

  return (
    <Card className="border-border/60 shadow-xl">
      <CardContent className="flex flex-col items-center gap-4 py-12 text-center">
        {status === "pending" && <Spinner className="h-6 w-6" />}
        {status === "success" && (
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-success/15">
            <CheckCircle2 className="h-6 w-6 text-success" />
          </div>
        )}
        {status === "error" && (
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-destructive/15">
            <XCircle className="h-6 w-6 text-destructive" />
          </div>
        )}
        <p className="text-sm text-muted-foreground">{message}</p>
        {status !== "pending" && (
          <Button asChild className="mt-2">
            <Link href={isAuthenticated ? "/dashboard" : "/login"}>Continue</Link>
          </Button>
        )}
      </CardContent>
    </Card>
  );
}

export default function VerifyEmailPage() {
  return (
    <div className="auth-backdrop flex min-h-screen flex-col items-center justify-center px-4 py-12">
      <div className="mb-8">
        <Link href="/">
          <Logo />
        </Link>
      </div>
      <div className="w-full max-w-md animate-fade-in">
        <Suspense fallback={<FullPageSpinner />}>
          <VerifyEmailInner />
        </Suspense>
      </div>
    </div>
  );
}
