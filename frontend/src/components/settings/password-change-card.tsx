"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { MailCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import { PasswordStrengthMeter } from "@/components/ui/password-strength-meter";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { getApiErrorMessage } from "@/lib/api/client";
import { useAuthStore } from "@/lib/stores/auth-store";
import {
  useAuth,
  useRequestPasswordChangeOtp,
  useVerifyPasswordChangeOtp,
  useCompletePasswordChange,
} from "@/lib/hooks/use-auth";
import {
  changePasswordSchema,
  verifyOtpSchema,
  confirmPasswordChangeSchema,
  type ChangePasswordValues,
  type VerifyOtpValues,
  type ConfirmPasswordChangeValues,
} from "@/lib/validations";

type Step = "request" | "verify" | "complete";

const STEPS: { key: Step; label: string }[] = [
  { key: "request", label: "Verify it's you" },
  { key: "verify", label: "Enter code" },
  { key: "complete", label: "New password" },
];

/**
 * The OTP password-change flow (ADR 0017): current password → emailed 6-digit
 * code → verify → new password → every session signed out.
 */
export function PasswordChangeCard() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const [step, setStep] = useState<Step>("request");
  const [ticket, setTicket] = useState<string | null>(null);

  const requestOtp = useRequestPasswordChangeOtp();
  const verifyOtp = useVerifyPasswordChangeOtp();
  const complete = useCompletePasswordChange();

  const requestForm = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    mode: "onBlur",
  });
  const verifyForm = useForm<VerifyOtpValues>({ resolver: zodResolver(verifyOtpSchema), mode: "onBlur" });
  const completeForm = useForm<ConfirmPasswordChangeValues>({
    resolver: zodResolver(confirmPasswordChangeSchema),
    mode: "onBlur",
  });
  const newPassword = completeForm.watch("newPassword") ?? "";

  const activeIndex = STEPS.findIndex((s) => s.key === step);

  const restart = () => {
    setStep("request");
    setTicket(null);
    requestForm.reset();
    verifyForm.reset();
    completeForm.reset();
  };

  const onRequest = (values: ChangePasswordValues) =>
    requestOtp.mutate(
      { currentPassword: values.currentPassword },
      {
        onSuccess: () => {
          requestForm.reset();
          setStep("verify");
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );

  const onVerify = (values: VerifyOtpValues) =>
    verifyOtp.mutate(
      { code: values.code },
      {
        onSuccess: (res) => {
          setTicket(res.ticket);
          verifyForm.reset();
          setStep("complete");
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );

  const onComplete = (values: ConfirmPasswordChangeValues) => {
    if (!ticket) {
      toast.error("Your verification expired — start again.");
      restart();
      return;
    }
    complete.mutate(
      { ticket, newPassword: values.newPassword },
      {
        onSuccess: () => {
          clearAuth();
          queryClient.clear();
          toast.success("Password changed. Please sign in with your new password.");
          router.replace("/login");
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Security</CardTitle>
        <CardDescription>
          Change your password. For your safety we first verify it&apos;s you with a 6-digit code sent to your
          email. All sessions are signed out afterwards.
        </CardDescription>
      </CardHeader>
      <CardContent className="max-w-md space-y-5">
        <ol className="flex items-center gap-1.5" aria-label="Progress">
          {STEPS.map((s, i) => (
            <li key={s.key} className="flex flex-1 items-center gap-1.5">
              <span
                className={cn(
                  "flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold transition-colors",
                  i < activeIndex && "bg-primary text-primary-foreground",
                  i === activeIndex && "bg-primary text-primary-foreground ring-2 ring-primary/30",
                  i > activeIndex && "bg-muted text-muted-foreground",
                )}
              >
                {i + 1}
              </span>
              <span
                className={cn(
                  "truncate text-[11px]",
                  i === activeIndex ? "font-medium text-foreground" : "text-muted-foreground",
                )}
              >
                {s.label}
              </span>
              {i < STEPS.length - 1 && <span className="h-px flex-1 bg-border" aria-hidden="true" />}
            </li>
          ))}
        </ol>

        {step === "request" && (
          <form onSubmit={requestForm.handleSubmit(onRequest)} className="space-y-4" noValidate>
            <Field
              label="Current password"
              htmlFor="currentPassword"
              error={requestForm.formState.errors.currentPassword?.message}
              hint="We'll email you a 6-digit code to confirm the change."
              required
            >
              <PasswordInput
                id="currentPassword"
                placeholder="••••••••"
                autoComplete="current-password"
                autoFocus
                {...requestForm.register("currentPassword")}
              />
            </Field>
            <div className="flex justify-end">
              <Button type="submit" loading={requestOtp.isPending}>
                Send code
              </Button>
            </div>
          </form>
        )}

        {step === "verify" && (
          <div className="space-y-4">
            <Alert variant="info">
              <MailCheck />
              <AlertDescription>
                Enter the 6-digit code we sent to{" "}
                <span className="font-medium text-foreground">{user?.email}</span>. It expires in 5 minutes.
              </AlertDescription>
            </Alert>
            <form onSubmit={verifyForm.handleSubmit(onVerify)} className="space-y-4" noValidate>
              <Field label="Verification code" htmlFor="otp-code" error={verifyForm.formState.errors.code?.message} required>
                <Input
                  id="otp-code"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  placeholder="123456"
                  autoFocus
                  className="tracking-[0.5em]"
                  {...verifyForm.register("code")}
                />
              </Field>
              <div className="flex items-center justify-between">
                <Button type="button" variant="ghost" size="sm" onClick={restart}>
                  Start over
                </Button>
                <Button type="submit" loading={verifyOtp.isPending}>
                  Verify code
                </Button>
              </div>
            </form>
          </div>
        )}

        {step === "complete" && (
          <form onSubmit={completeForm.handleSubmit(onComplete)} className="space-y-4" noValidate>
            <Field
              label="New password"
              htmlFor="newPassword"
              error={completeForm.formState.errors.newPassword?.message}
              hint="At least 8 characters, with upper, lower and a digit."
              required
            >
              <PasswordInput
                id="newPassword"
                placeholder="••••••••"
                autoComplete="new-password"
                autoFocus
                {...completeForm.register("newPassword")}
              />
            </Field>
            <PasswordStrengthMeter value={newPassword} />
            <Field
              label="Confirm password"
              htmlFor="confirmPassword"
              error={completeForm.formState.errors.confirmPassword?.message}
              required
            >
              <PasswordInput
                id="confirmPassword"
                placeholder="••••••••"
                autoComplete="new-password"
                {...completeForm.register("confirmPassword")}
              />
            </Field>
            <div className="flex items-center justify-between">
              <Button type="button" variant="ghost" size="sm" onClick={restart}>
                Start over
              </Button>
              <Button type="submit" loading={complete.isPending}>
                Update password
              </Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
