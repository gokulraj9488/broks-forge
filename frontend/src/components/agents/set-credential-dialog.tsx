"use client";

import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRound } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useSetCredential } from "@/lib/hooks/use-agents";
import { getApiErrorMessage } from "@/lib/api/client";
import { AUTH_TYPE_OPTIONS } from "@/lib/api/agents";
import { setCredentialSchema, type SetCredentialValues } from "@/lib/validations";

export function SetCredentialDialog({
  organizationId,
  projectId,
  agentId,
}: {
  organizationId: string;
  projectId: string;
  agentId: string;
}) {
  const [open, setOpen] = useState(false);
  const setCredential = useSetCredential(organizationId, projectId, agentId);
  const {
    register,
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<SetCredentialValues>({
    resolver: zodResolver(setCredentialSchema),
    defaultValues: { authType: "API_KEY" },
  });

  const authType = watch("authType");
  const needsSecret = authType !== "NONE";
  const needsUsername = authType === "BASIC_AUTH";
  const needsHeaderName = authType === "CUSTOM_HEADER" || authType === "API_KEY";

  const onSubmit = (values: SetCredentialValues) => {
    setCredential.mutate(
      {
        authType: values.authType,
        secret: values.secret || undefined,
        username: values.username || undefined,
        headerName: values.headerName || undefined,
      },
      {
        onSuccess: () => {
          toast.success("Credential saved");
          setOpen(false);
          reset({ authType: "API_KEY" });
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <KeyRound className="h-4 w-4" />
          Set credential
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Set agent credential</DialogTitle>
          <DialogDescription>
            The secret is encrypted at rest and never shown again. Saving replaces the current credential.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <Field label="Authentication type" htmlFor="cred-type" error={errors.authType?.message} required>
            <Controller
              control={control}
              name="authType"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="cred-type" onBlur={field.onBlur}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {AUTH_TYPE_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </Field>

          {needsHeaderName && (
            <Field
              label="Header name"
              htmlFor="cred-header"
              error={errors.headerName?.message}
              hint={authType === "API_KEY" ? "Defaults to X-API-Key if left blank" : undefined}
              required={authType === "CUSTOM_HEADER"}
            >
              <Input id="cred-header" placeholder="X-API-Key" {...register("headerName")} />
            </Field>
          )}

          {needsUsername && (
            <Field label="Username" htmlFor="cred-username" error={errors.username?.message} required>
              <Input id="cred-username" autoComplete="off" {...register("username")} />
            </Field>
          )}

          {needsSecret && (
            <Field
              label={authType === "BASIC_AUTH" ? "Password" : "Secret"}
              htmlFor="cred-secret"
              error={errors.secret?.message}
              required
            >
              <PasswordInput id="cred-secret" autoComplete="off" {...register("secret")} />
            </Field>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={setCredential.isPending}>
              Save credential
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
