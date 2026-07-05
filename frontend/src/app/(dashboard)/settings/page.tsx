"use client";

import { useEffect, useState } from "react";
import { useTheme } from "next-themes";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { MailCheck, Moon, Sun } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { PasswordInput } from "@/components/ui/password-input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PageHeader } from "@/components/layout/page-header";
import { useChangePassword } from "@/lib/hooks/use-auth";
import { getApiErrorMessage } from "@/lib/api/client";
import { changePasswordSchema, type ChangePasswordValues } from "@/lib/validations";

export default function SettingsPage() {
  const { theme, setTheme } = useTheme();
  const changePassword = useChangePassword();
  const [linkSent, setLinkSent] = useState(false);
  // next-themes only knows the real theme after mount; render theme UI then to
  // avoid a wrong "dark" flash for light-theme users during hydration.
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ChangePasswordValues>({ resolver: zodResolver(changePasswordSchema) });

  const onSubmit = (values: ChangePasswordValues) => {
    changePassword.mutate(
      { currentPassword: values.currentPassword },
      {
        onSuccess: () => {
          reset();
          setLinkSent(true);
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  return (
    <div>
      <PageHeader title="Settings" description="Manage your account preferences and security." />

      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Appearance</CardTitle>
            <CardDescription>Choose how Brok&apos;s Forge looks to you.</CardDescription>
          </CardHeader>
          <CardContent>
            {mounted ? (
              <>
                <Field label="Theme" htmlFor="theme" className="max-w-xs">
                  <Select
                    value={theme === "light" ? "light" : "dark"}
                    onValueChange={(value) => setTheme(value)}
                  >
                    <SelectTrigger id="theme">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="dark">Dark</SelectItem>
                      <SelectItem value="light">Light</SelectItem>
                    </SelectContent>
                  </Select>
                </Field>
                <div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
                  {theme === "light" ? (
                    <Sun className="h-3.5 w-3.5" />
                  ) : (
                    <Moon className="h-3.5 w-3.5" />
                  )}
                  Currently using the {theme === "light" ? "light" : "dark"} theme.
                </div>
              </>
            ) : (
              <div className="h-[4.75rem] max-w-xs animate-pulse rounded-md bg-muted/50" />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Security</CardTitle>
            <CardDescription>
              Change your password. For your safety we first verify it&apos;s you: confirm your
              current password and we&apos;ll email you a link to choose the new one. All sessions
              are signed out afterwards.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {linkSent ? (
              <div className="flex max-w-md items-start gap-3 rounded-md border border-success/30 bg-success/10 px-4 py-3 animate-in fade-in-0 slide-in-from-top-1 duration-200">
                <MailCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden="true" />
                <div className="space-y-1 text-sm">
                  <p className="font-medium">Check your email</p>
                  <p className="text-muted-foreground">
                    We sent you a link to confirm your password change. It expires in 15 minutes.
                    Didn&apos;t get it?{" "}
                    <button
                      type="button"
                      onClick={() => setLinkSent(false)}
                      className="font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-sm"
                    >
                      Try again
                    </button>
                    .
                  </p>
                </div>
              </div>
            ) : (
              <form onSubmit={handleSubmit(onSubmit)} className="max-w-md space-y-4" noValidate>
                <Field
                  label="Current password"
                  htmlFor="currentPassword"
                  error={errors.currentPassword?.message}
                  hint="We'll email you a verification link to choose your new password."
                  required
                >
                  <PasswordInput
                    id="currentPassword"
                    placeholder="••••••••"
                    autoComplete="current-password"
                    {...register("currentPassword")}
                  />
                </Field>
                <div className="flex justify-end">
                  <Button type="submit" loading={changePassword.isPending}>
                    Send verification link
                  </Button>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
