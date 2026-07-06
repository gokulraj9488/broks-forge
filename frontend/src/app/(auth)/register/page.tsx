"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import { PasswordStrengthMeter } from "@/components/ui/password-strength-meter";
import { useRegister } from "@/lib/hooks/use-auth";
import { getApiErrorMessage } from "@/lib/api/client";
import { registerSchema, type RegisterValues } from "@/lib/validations";

export default function RegisterPage() {
  const router = useRouter();
  const registerMutation = useRegister();
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema), mode: "onBlur" });
  const password = watch("password") ?? "";

  const onSubmit = (values: RegisterValues) => {
    registerMutation.mutate(values, {
      onSuccess: () => {
        toast.success("Account created. Welcome to Brok's Forge!");
        router.replace("/dashboard");
      },
      onError: (error) => toast.error(getApiErrorMessage(error, "Unable to create account")),
    });
  };

  return (
    <Card className="border-border/60 shadow-xl">
      <CardHeader>
        <CardTitle className="text-xl">Create your account</CardTitle>
        <CardDescription>Start building the engineering platform for your AI agents.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="First name" htmlFor="firstName" error={errors.firstName?.message}>
              <Input id="firstName" placeholder="Ada" autoComplete="given-name" {...register("firstName")} />
            </Field>
            <Field label="Last name" htmlFor="lastName" error={errors.lastName?.message}>
              <Input id="lastName" placeholder="Lovelace" autoComplete="family-name" {...register("lastName")} />
            </Field>
          </div>
          <Field label="Email" htmlFor="email" error={errors.email?.message} required>
            <Input
              id="email"
              type="email"
              placeholder="you@example.com"
              autoComplete="email"
              {...register("email")}
            />
          </Field>
          <Field
            label="Password"
            htmlFor="password"
            error={errors.password?.message}
            hint="At least 8 characters, with upper, lower and a digit."
            required
          >
            <PasswordInput
              id="password"
              placeholder="••••••••"
              autoComplete="new-password"
              {...register("password")}
            />
          </Field>
          <PasswordStrengthMeter value={password} className="-mt-2" />
          <Button type="submit" className="w-full" loading={registerMutation.isPending}>
            Create account
          </Button>
        </form>
        <p className="mt-6 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
