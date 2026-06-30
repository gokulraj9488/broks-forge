"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { BadgeCheck, MailWarning } from "lucide-react";
import { toast } from "sonner";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FullPageSpinner } from "@/components/ui/spinner";
import { PageHeader } from "@/components/layout/page-header";
import { useAuth, useUpdateProfile } from "@/lib/hooks/use-auth";
import { authApi } from "@/lib/api/auth";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDateTime, initials } from "@/lib/utils";
import { profileSchema, type ProfileValues } from "@/lib/validations";

export default function ProfilePage() {
  const { user, isLoading } = useAuth();
  const updateProfile = useUpdateProfile();
  const [resending, setResending] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { firstName: "", lastName: "" },
  });

  useEffect(() => {
    if (user) {
      reset({ firstName: user.firstName ?? "", lastName: user.lastName ?? "" });
    }
  }, [user, reset]);

  if (isLoading || !user) {
    return <FullPageSpinner />;
  }

  const onSubmit = (values: ProfileValues) => {
    updateProfile.mutate(values, {
      onSuccess: () => toast.success("Profile updated"),
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });
  };

  const handleResend = async () => {
    setResending(true);
    try {
      await authApi.resendVerification(user.email);
      toast.success("Verification email sent");
    } catch (error) {
      toast.error(getApiErrorMessage(error));
    } finally {
      setResending(false);
    }
  };

  return (
    <div>
      <PageHeader title="Profile" description="Your personal information and account status." />

      <div className="space-y-6">
        <Card>
          <CardContent className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center">
            <Avatar className="h-16 w-16 text-lg">
              <AvatarFallback>{initials(user.fullName || user.email)}</AvatarFallback>
            </Avatar>
            <div className="space-y-1">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-lg font-semibold">{user.fullName || user.email}</h2>
                {user.emailVerified ? (
                  <Badge variant="success" className="gap-1">
                    <BadgeCheck className="h-3 w-3" />
                    Verified
                  </Badge>
                ) : (
                  <Badge variant="muted" className="gap-1">
                    <MailWarning className="h-3 w-3" />
                    Unverified
                  </Badge>
                )}
              </div>
              <p className="text-sm text-muted-foreground">{user.email}</p>
              <div className="flex flex-wrap gap-1.5 pt-1">
                {user.roles.map((role) => (
                  <Badge key={role} variant="outline" className="text-[10px]">
                    {role}
                  </Badge>
                ))}
              </div>
            </div>
          </CardContent>
        </Card>

        {!user.emailVerified && (
          <Card className="border-amber-500/30 bg-amber-500/5">
            <CardContent className="flex flex-col items-start justify-between gap-3 p-4 sm:flex-row sm:items-center">
              <div className="flex items-start gap-2 text-sm">
                <MailWarning className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
                <span className="text-muted-foreground">
                  Your email isn&apos;t verified yet. Check your inbox or resend the link.
                </span>
              </div>
              <Button variant="outline" size="sm" loading={resending} onClick={handleResend}>
                Resend email
              </Button>
            </CardContent>
          </Card>
        )}

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Personal information</CardTitle>
            <CardDescription>Update how your name appears across the platform.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="First name" htmlFor="firstName" error={errors.firstName?.message}>
                  <Input id="firstName" {...register("firstName")} />
                </Field>
                <Field label="Last name" htmlFor="lastName" error={errors.lastName?.message}>
                  <Input id="lastName" {...register("lastName")} />
                </Field>
              </div>
              <Field label="Email" hint="Email changes require re-verification and are coming soon.">
                <Input value={user.email} disabled readOnly />
              </Field>
              <p className="text-xs text-muted-foreground">
                Last sign in: {formatDateTime(user.lastLoginAt)}
              </p>
              <div className="flex justify-end">
                <Button type="submit" loading={updateProfile.isPending} disabled={!isDirty}>
                  Save changes
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
