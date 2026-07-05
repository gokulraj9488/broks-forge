"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { PasswordInput } from "@/components/ui/password-input";
import { FullPageSpinner } from "@/components/ui/spinner";
import { Logo } from "@/components/brand/logo";
import { authApi } from "@/lib/api/auth";
import { getApiErrorMessage } from "@/lib/api/client";
import { useAuthStore } from "@/lib/stores/auth-store";
import { confirmPasswordChangeSchema, type ConfirmPasswordChangeValues } from "@/lib/validations";

/**
 * Landing page for the emailed password-change confirmation link. Works whether
 * or not the user is still signed in (so it lives outside the guarded route
 * groups). On success every session is revoked server-side, so the local
 * session is cleared too and the user signs in again.
 */
function ChangePasswordForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const token = searchParams.get("token") ?? "";
  const [pending, setPending] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ConfirmPasswordChangeValues>({ resolver: zodResolver(confirmPasswordChangeSchema) });

  const onSubmit = async (values: ConfirmPasswordChangeValues) => {
    if (!token) {
      toast.error("Missing or invalid confirmation token");
      return;
    }
    setPending(true);
    try {
      await authApi.confirmPasswordChange(token, values.newPassword);
      clearAuth();
      queryClient.clear();
      toast.success("Password changed. Please sign in with your new password.");
      router.replace("/login");
    } catch (error) {
      toast.error(getApiErrorMessage(error, "Unable to change password"));
    } finally {
      setPending(false);
    }
  };

  return (
    <Card className="border-border/60 shadow-xl">
      <CardHeader>
        <CardTitle className="text-xl">Confirm your password change</CardTitle>
        <CardDescription>
          Choose your new password. Every existing session will be signed out.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {!token && (
          <p className="mb-4 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
            This confirmation link is missing its token. Request a new one from Settings.
          </p>
        )}
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <Field
            label="New password"
            htmlFor="newPassword"
            error={errors.newPassword?.message}
            hint="At least 8 characters, with upper, lower and a digit."
            required
          >
            <PasswordInput
              id="newPassword"
              placeholder="••••••••"
              autoComplete="new-password"
              {...register("newPassword")}
            />
          </Field>
          <Field
            label="Confirm password"
            htmlFor="confirmPassword"
            error={errors.confirmPassword?.message}
            required
          >
            <PasswordInput
              id="confirmPassword"
              placeholder="••••••••"
              autoComplete="new-password"
              {...register("confirmPassword")}
            />
          </Field>
          <Button type="submit" className="w-full" loading={pending} disabled={!token}>
            Change password
          </Button>
        </form>
        <p className="mt-6 text-center text-sm text-muted-foreground">
          <Link href="/login" className="font-medium text-primary hover:underline">
            Back to sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}

export default function ChangePasswordPage() {
  return (
    <div className="auth-backdrop flex min-h-screen flex-col items-center justify-center px-4 py-12">
      <div className="mb-8">
        <Link href="/">
          <Logo />
        </Link>
      </div>
      <div className="w-full max-w-md animate-fade-in">
        <Suspense fallback={<FullPageSpinner />}>
          <ChangePasswordForm />
        </Suspense>
      </div>
    </div>
  );
}
