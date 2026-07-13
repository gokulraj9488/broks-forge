"use client";

import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Pencil, Plus } from "lucide-react";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useCreateProvider, useUpdateProvider } from "@/lib/hooks/use-providers";
import { PROVIDER_TYPE_OPTIONS, type ProviderResponse } from "@/lib/api/providers";
import { AUTH_TYPE_OPTIONS, type AgentAuthType, type LlmProvider } from "@/lib/api/agents";
import { getApiErrorMessage } from "@/lib/api/client";

const schema = z.object({
  name: z.string().min(1, "Name is required").max(120),
  type: z.string().min(1, "Select a provider type"),
  baseUrl: z.string().min(1, "Base URL is required").max(2048),
  authType: z.string().min(1),
  apiKey: z.string().optional(),
  defaultModel: z.string().max(128).optional(),
  supportedModels: z.string().optional(),
});

type Values = z.infer<typeof schema>;

function toModelList(value: string | undefined): string[] | undefined {
  if (!value) return undefined;
  const list = value
    .split(",")
    .map((m) => m.trim())
    .filter(Boolean);
  return list.length > 0 ? list : undefined;
}

export function ProviderFormDialog({
  organizationId,
  projectId,
  provider,
  open: controlledOpen,
  onOpenChange: setControlledOpen,
}: {
  organizationId: string;
  projectId: string;
  provider?: ProviderResponse;
  /** Controlled mode (no built-in trigger button) — used when triggered from a dropdown menu item. */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}) {
  const isEdit = !!provider;
  const isControlled = controlledOpen !== undefined;
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const open = isControlled ? controlledOpen : uncontrolledOpen;
  const setOpen = isControlled ? (setControlledOpen as (open: boolean) => void) : setUncontrolledOpen;
  const create = useCreateProvider(organizationId, projectId);
  const update = useUpdateProvider(organizationId, projectId, provider?.id ?? "");
  const mutation = isEdit ? update : create;

  const {
    register,
    handleSubmit,
    reset,
    control,
    watch,
    setValue,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: provider?.name ?? "",
      type: provider?.type ?? "",
      baseUrl: provider?.baseUrl ?? "",
      authType: provider?.authType ?? "NONE",
      apiKey: "",
      defaultModel: provider?.defaultModel ?? "",
      supportedModels: provider?.supportedModels?.join(", ") ?? "",
    },
  });

  // Reset to the provider's current values whenever the dialog re-opens in edit mode, so
  // stale edits from a previous open don't linger.
  useEffect(() => {
    if (open) {
      reset({
        name: provider?.name ?? "",
        type: provider?.type ?? "",
        baseUrl: provider?.baseUrl ?? "",
        authType: provider?.authType ?? "NONE",
        apiKey: "",
        defaultModel: provider?.defaultModel ?? "",
        supportedModels: provider?.supportedModels?.join(", ") ?? "",
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const authType = watch("authType");

  const onSubmit = (values: Values) => {
    const payload = {
      name: values.name,
      type: values.type as LlmProvider,
      baseUrl: values.baseUrl,
      authType: values.authType as AgentAuthType,
      apiKey: values.apiKey || undefined,
      defaultModel: values.defaultModel || undefined,
      supportedModels: toModelList(values.supportedModels),
    };

    mutation.mutate(payload, {
      onSuccess: () => {
        toast.success(isEdit ? "Provider updated" : "Provider created");
        setOpen(false);
        if (!isEdit) reset();
      },
      onError: (error) => toast.error(getApiErrorMessage(error)),
    });
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      {!isControlled && (
        <DialogTrigger asChild>
          {isEdit ? (
            <Button variant="ghost" size="sm">
              <Pencil className="h-4 w-4" />
              Edit
            </Button>
          ) : (
            <Button size="sm">
              <Plus className="h-4 w-4" />
              New provider
            </Button>
          )}
        </DialogTrigger>
      )}
      <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit provider" : "Register a provider"}</DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update this provider's configuration. Leave the API key blank to keep the stored one."
              : "The shared connection, authentication and capability profile agents can reference instead of duplicating provider configuration."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <Field label="Name" htmlFor="pv-name" error={errors.name?.message} required>
            <Input id="pv-name" placeholder="Groq Production" {...register("name")} />
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Type" htmlFor="pv-type" error={errors.type?.message} required>
              <Controller
                control={control}
                name="type"
                render={({ field }) => (
                  <Select
                    value={field.value || undefined}
                    onValueChange={(v) => {
                      field.onChange(v);
                      const option = PROVIDER_TYPE_OPTIONS.find((o) => o.value === v);
                      // Auto-fill a sensible base URL only if the field is still empty, so
                      // switching type never clobbers a URL the user already typed/edited.
                      if (option?.defaultBaseUrl && !watch("baseUrl")) {
                        setValue("baseUrl", option.defaultBaseUrl);
                      }
                    }}
                    disabled={isEdit}
                  >
                    <SelectTrigger id="pv-type" onBlur={field.onBlur}>
                      <SelectValue placeholder="Select a type" />
                    </SelectTrigger>
                    <SelectContent>
                      {PROVIDER_TYPE_OPTIONS.map((o) => (
                        <SelectItem key={o.value} value={o.value}>
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </Field>
            <Field label="Auth type" htmlFor="pv-auth" error={errors.authType?.message} required>
              <Controller
                control={control}
                name="authType"
                render={({ field }) => (
                  <Select value={field.value || undefined} onValueChange={field.onChange}>
                    <SelectTrigger id="pv-auth" onBlur={field.onBlur}>
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
          </div>

          <Field label="Base URL" htmlFor="pv-base-url" error={errors.baseUrl?.message} required>
            <Input
              id="pv-base-url"
              placeholder="https://api.groq.com/openai/v1/chat/completions"
              {...register("baseUrl")}
            />
          </Field>

          {authType !== "NONE" && (
            <Field
              label="API key"
              htmlFor="pv-api-key"
              error={errors.apiKey?.message}
              hint={
                isEdit
                  ? provider?.apiKeyConfigured
                    ? `Currently set (${provider.apiKeyHint}). Leave blank to keep it.`
                    : "No key stored yet."
                  : "Encrypted at rest; never shown again after saving."
              }
            >
              <Input id="pv-api-key" type="password" placeholder="sk-…" {...register("apiKey")} />
            </Field>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <Field
              label="Default model"
              htmlFor="pv-default-model"
              error={errors.defaultModel?.message}
              hint="Optional"
            >
              <Input id="pv-default-model" placeholder="llama-3.3-70b-versatile" {...register("defaultModel")} />
            </Field>
            <Field
              label="Supported models"
              htmlFor="pv-supported-models"
              error={errors.supportedModels?.message}
              hint="Comma-separated, optional"
            >
              <Input
                id="pv-supported-models"
                placeholder="llama-3.3-70b-versatile, llama-3.1-8b-instant"
                {...register("supportedModels")}
              />
            </Field>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={mutation.isPending}>
              {isEdit ? "Save changes" : "Create provider"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
