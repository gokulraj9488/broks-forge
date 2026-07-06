"use client";

import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRound, PlugZap } from "lucide-react";
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
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useSetCredential, useUpdateCredential, useTestDraftCredential } from "@/lib/hooks/use-agents";
import { getApiErrorMessage } from "@/lib/api/client";
import {
  AUTH_TYPE_OPTIONS,
  type AgentAuthType,
  type AgentCredentialResponse,
  type CredentialTestResult,
} from "@/lib/api/agents";
import { setCredentialSchema, type SetCredentialValues } from "@/lib/validations";

interface Props {
  organizationId: string;
  projectId: string;
  agentId: string;
  /** When provided, the dialog edits this credential in place instead of creating one. */
  credential?: AgentCredentialResponse;
  trigger?: React.ReactNode;
  /** Controlled open state. When provided, the dialog is driven by the parent (e.g. onboarding auto-open). */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  /** Called with the saved credential after a successful create/update. */
  onSaved?: (credential: AgentCredentialResponse) => void;
  /** Pre-selected auth type when creating (defaults to API Key). Mirrors the agent's declared auth type. */
  initialAuthType?: AgentAuthType;
}

export function SetCredentialDialog({
  organizationId,
  projectId,
  agentId,
  credential,
  trigger,
  open: openProp,
  onOpenChange,
  onSaved,
  initialAuthType,
}: Props) {
  const isEdit = !!credential;
  const controlled = openProp !== undefined;
  const [openState, setOpenState] = useState(false);
  const open = controlled ? openProp : openState;
  const setOpen = (next: boolean) => {
    if (!controlled) setOpenState(next);
    onOpenChange?.(next);
  };
  const [testResult, setTestResult] = useState<CredentialTestResult | null>(null);

  const setCredential = useSetCredential(organizationId, projectId, agentId);
  const updateCredential = useUpdateCredential(organizationId, projectId, agentId);
  const testDraft = useTestDraftCredential(organizationId, projectId, agentId);

  const defaults: Partial<SetCredentialValues> = isEdit
    ? {
        label: credential?.label ?? "",
        authType: credential?.authType ?? "API_KEY",
        username: credential?.username ?? "",
        headerName: credential?.headerName ?? "",
        headerPrefix: credential?.headerPrefix ?? "",
        keepSecret: true,
      }
    : { authType: initialAuthType ?? "API_KEY" };

  const {
    register,
    control,
    handleSubmit,
    reset,
    watch,
    getValues,
    formState: { errors },
  } = useForm<SetCredentialValues>({ resolver: zodResolver(setCredentialSchema), defaultValues: defaults });

  const authType = watch("authType");
  const needsSecret = authType !== "NONE";
  const needsUsername = authType === "BASIC_AUTH";
  const needsHeaderName = authType === "CUSTOM_HEADER" || authType === "API_KEY";
  const needsPrefix = authType === "API_KEY" || authType === "BEARER_TOKEN" || authType === "CUSTOM_HEADER";

  const resetAll = () => {
    reset(defaults);
    setTestResult(null);
  };

  const onSubmit = (values: SetCredentialValues) => {
    const payload = {
      label: values.label || undefined,
      authType: values.authType,
      secret: values.secret || undefined,
      username: values.username || undefined,
      headerName: values.headerName || undefined,
      headerPrefix: values.headerPrefix || undefined,
    };
    const onSuccess = (saved: AgentCredentialResponse) => {
      toast.success(isEdit ? "Credential updated" : "Credential saved");
      setOpen(false);
      resetAll();
      onSaved?.(saved);
    };
    const onError = (error: unknown) => toast.error(getApiErrorMessage(error));

    if (isEdit && credential) {
      updateCredential.mutate({ credentialId: credential.id, payload }, { onSuccess, onError });
    } else {
      setCredential.mutate(payload, { onSuccess, onError });
    }
  };

  const runTest = () => {
    const values = getValues();
    if (values.authType !== "NONE" && !values.secret) {
      toast.error("Enter a secret to test the connection");
      return;
    }
    testDraft.mutate(
      {
        authType: values.authType,
        secret: values.secret || undefined,
        username: values.username || undefined,
        headerName: values.headerName || undefined,
        headerPrefix: values.headerPrefix || undefined,
      },
      {
        onSuccess: (result) => setTestResult(result),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const pending = setCredential.isPending || updateCredential.isPending;

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) resetAll();
      }}
    >
      {(!controlled || trigger) && (
        <DialogTrigger asChild>
          {trigger ?? (
            <Button size="sm">
              <KeyRound className="h-4 w-4" />
              Set credential
            </Button>
          )}
        </DialogTrigger>
      )}
      <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit credential" : "Set agent credential"}</DialogTitle>
          <DialogDescription>
            The secret is encrypted at rest and never shown again.{" "}
            {isEdit
              ? "Leave the secret blank to keep the current one."
              : "Setting a new credential replaces the active one."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <Field label="Credential name" htmlFor="cred-label" error={errors.label?.message} hint="Optional, e.g. “Groq production key”.">
            <Input id="cred-label" placeholder="Groq production key" {...register("label")} />
          </Field>

          <Field label="Authentication type" htmlFor="cred-type" error={errors.authType?.message} required>
            <Controller
              control={control}
              name="authType"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(v) => {
                    field.onChange(v);
                    setTestResult(null);
                  }}
                >
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
              <Input id="cred-header" placeholder="Authorization" {...register("headerName")} />
            </Field>
          )}

          {needsPrefix && (
            <Field
              label="Header prefix"
              htmlFor="cred-prefix"
              error={errors.headerPrefix?.message}
              hint={
                authType === "BEARER_TOKEN"
                  ? "Prepended to the token. Defaults to “Bearer”."
                  : "Optional, e.g. “Bearer” or “Token”. Leave blank to send the raw value."
              }
            >
              <Input id="cred-prefix" placeholder="Bearer" {...register("headerPrefix")} />
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
              hint={isEdit ? "Leave blank to keep the current secret." : undefined}
              required={!isEdit}
            >
              <PasswordInput id="cred-secret" autoComplete="off" placeholder="gsk_••••••••" {...register("secret")} />
            </Field>
          )}

          {testResult && (
            <Alert variant={testResult.success ? "success" : "destructive"}>
              <PlugZap />
              <AlertDescription>
                {testResult.message}
                {testResult.latencyMs > 0 && <span className="text-muted-foreground"> · {testResult.latencyMs}ms</span>}
              </AlertDescription>
            </Alert>
          )}

          <DialogFooter className="gap-2 sm:justify-between">
            <Button type="button" variant="outline" onClick={runTest} loading={testDraft.isPending}>
              <PlugZap className="h-4 w-4" />
              Test connection
            </Button>
            <div className="flex gap-2">
              <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" loading={pending}>
                {isEdit ? "Save changes" : "Save credential"}
              </Button>
            </div>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
