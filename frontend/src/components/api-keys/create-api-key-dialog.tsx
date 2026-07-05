"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Check, Copy, KeyRound, Plus, TriangleAlert } from "lucide-react";
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
import { useCreateApiKey } from "@/lib/hooks/use-api-keys";
import { getApiErrorMessage } from "@/lib/api/client";
import { apiKeySchema, type ApiKeyValues } from "@/lib/validations";
import type { CreatedApiKeyResponse } from "@/lib/api/types";

export function CreateApiKeyDialog({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const [open, setOpen] = useState(false);
  const [created, setCreated] = useState<CreatedApiKeyResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const create = useCreateApiKey(organizationId, projectId);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ApiKeyValues>({ resolver: zodResolver(apiKeySchema) });

  const close = () => {
    setOpen(false);
    setCreated(null);
    setCopied(false);
    reset();
  };

  const onSubmit = (values: ApiKeyValues) => {
    create.mutate(
      { name: values.name, expiresInDays: values.expiresInDays },
      {
        onSuccess: (data) => setCreated(data),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const copyKey = async () => {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.plaintextKey);
      setCopied(true);
      toast.success("Key copied to clipboard");
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Couldn't copy — copy it manually");
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? setOpen(true) : close())}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus className="h-4 w-4" />
          New API key
        </Button>
      </DialogTrigger>
      <DialogContent>
        {created ? (
          <>
            <DialogHeader>
              <DialogTitle>API key created</DialogTitle>
              <DialogDescription>
                Copy your key now. For security, it will not be shown again.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-3">
              <div className="flex items-center gap-2 rounded-md border border-border bg-muted/40 p-3">
                <code className="flex-1 break-all font-mono text-xs">{created.plaintextKey}</code>
                <Button variant="outline" size="icon" className="h-8 w-8 shrink-0" onClick={copyKey}>
                  {copied ? <Check className="h-4 w-4 text-success" /> : <Copy className="h-4 w-4" />}
                </Button>
              </div>
              <p className="flex items-start gap-2 text-xs text-muted-foreground">
                <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0 text-warning" />
                Store this in a secret manager. Anyone with this key can access the project.
              </p>
            </div>
            <DialogFooter>
              <Button onClick={close}>Done</Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <KeyRound className="h-4 w-4" />
                Create API key
              </DialogTitle>
              <DialogDescription>Issue a programmatic key scoped to this project.</DialogDescription>
            </DialogHeader>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <Field label="Name" htmlFor="key-name" error={errors.name?.message} required>
                <Input id="key-name" placeholder="CI pipeline" {...register("name")} />
              </Field>
              <Field
                label="Expires in (days)"
                htmlFor="key-expiry"
                error={errors.expiresInDays?.message}
                hint="Optional. Leave blank for a non-expiring key."
              >
                <Input id="key-expiry" type="number" min={1} max={3650} placeholder="90" {...register("expiresInDays")} />
              </Field>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={close}>
                  Cancel
                </Button>
                <Button type="submit" loading={create.isPending}>
                  Create key
                </Button>
              </DialogFooter>
            </form>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
