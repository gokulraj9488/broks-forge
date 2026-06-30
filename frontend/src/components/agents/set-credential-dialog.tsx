"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
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
import { Select } from "@/components/ui/select";
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
            <Select id="cred-type" {...register("authType")}>
              {AUTH_TYPE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </Select>
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
              <Input id="cred-secret" type="password" autoComplete="off" {...register("secret")} />
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
