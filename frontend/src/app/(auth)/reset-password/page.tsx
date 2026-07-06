"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { PasswordInput } from "@/components/ui/password-input";
import { PasswordStrengthMeter } from "@/components/ui/password-strength-meter";
import { FullPageSpinner } from "@/components/ui/spinner";
import { authApi } from "@/lib/api/auth";
import { getApiErrorMessage } from "@/lib/api/client";
import { resetPasswordSchema, type ResetPasswordValues } from "@/lib/validations";

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const [pending, setPending] = useState(false);
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ResetPasswordValues>({ resolver: zodResolver(resetPasswordSchema), mode: "onBlur" });
  const newPassword = watch("newPassword") ?? "";

  const onSubmit = async (values: ResetPasswordValues) => {
    if (!token) {
      toast.error("Missing or invalid reset token");
      return;
    }
    setPending(true);
    try {
      await authApi.resetPassword(token, values.newPassword);
      toast.success("Password reset. Please sign in.");
      router.replace("/login");
    } catch (error) {
      toast.error(getApiErrorMessage(error, "Unable to reset password"));
    } finally {
      setPending(false);
    }
  };

  return (
    <Card className="border-border/60 shadow-xl">
      <CardHeader>
        <CardTitle className="text-xl">Set a new password</CardTitle>
        <CardDescription>Choose a strong password you don&apos;t use elsewhere.</CardDescription>
      </CardHeader>
      <CardContent>
        {!token && (
          <p className="mb-4 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
            This reset link is missing its token. Request a new link.
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
          <PasswordStrengthMeter value={newPassword} className="-mt-2" />
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
            Reset password
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

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <ResetPasswordForm />
    </Suspense>
  );
}
