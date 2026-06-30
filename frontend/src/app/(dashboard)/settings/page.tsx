"use client";

import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Moon, Sun } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { PageHeader } from "@/components/layout/page-header";
import { useChangePassword } from "@/lib/hooks/use-auth";
import { useAuthStore } from "@/lib/stores/auth-store";
import { getApiErrorMessage } from "@/lib/api/client";
import { changePasswordSchema, type ChangePasswordValues } from "@/lib/validations";

export default function SettingsPage() {
  const router = useRouter();
  const { theme, setTheme } = useTheme();
  const changePassword = useChangePassword();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ChangePasswordValues>({ resolver: zodResolver(changePasswordSchema) });

  const onSubmit = (values: ChangePasswordValues) => {
    changePassword.mutate(
      { currentPassword: values.currentPassword, newPassword: values.newPassword },
      {
        onSuccess: () => {
          reset();
          toast.success("Password changed. Please sign in again.");
          clearAuth();
          router.replace("/login");
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
            <Field label="Theme" htmlFor="theme" className="max-w-xs">
              <div className="relative">
                <Select
                  id="theme"
                  value={theme === "light" ? "light" : "dark"}
                  onChange={(e) => setTheme(e.target.value)}
                >
                  <option value="dark">Dark</option>
                  <option value="light">Light</option>
                </Select>
              </div>
            </Field>
            <div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
              {theme === "light" ? <Sun className="h-3.5 w-3.5" /> : <Moon className="h-3.5 w-3.5" />}
              Currently using the {theme === "light" ? "light" : "dark"} theme.
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Security</CardTitle>
            <CardDescription>
              Change your password. For your safety, all sessions are signed out afterwards.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)} className="max-w-md space-y-4" noValidate>
              <Field
                label="Current password"
                htmlFor="currentPassword"
                error={errors.currentPassword?.message}
                required
              >
                <Input
                  id="currentPassword"
                  type="password"
                  autoComplete="current-password"
                  {...register("currentPassword")}
                />
              </Field>
              <Field
                label="New password"
                htmlFor="newPassword"
                error={errors.newPassword?.message}
                hint="At least 8 characters, with upper, lower and a digit."
                required
              >
                <Input
                  id="newPassword"
                  type="password"
                  autoComplete="new-password"
                  {...register("newPassword")}
                />
              </Field>
              <Field
                label="Confirm new password"
                htmlFor="confirmPassword"
                error={errors.confirmPassword?.message}
                required
              >
                <Input
                  id="confirmPassword"
                  type="password"
                  autoComplete="new-password"
                  {...register("confirmPassword")}
                />
              </Field>
              <div className="flex justify-end">
                <Button type="submit" loading={changePassword.isPending}>
                  Update password
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
